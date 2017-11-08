/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.cursor

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.view.{MotionEvent, View}
import android.widget.Toast
import com.google.android.gms.common.{ConnectionResult, GoogleApiAvailability}
import com.waz.api._
import com.waz.content.UserPreferences
import com.waz.model.{ConversationData, MessageData}
import com.waz.service.ZMessaging
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.{Injectable, Injector, R}
import com.waz.zclient.common.controllers.{CameraPermission, PermissionsController, ReadExternalStoragePermission, RecordAudioPermission}
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.drawing.IDrawingController
import com.waz.zclient.controllers.giphy.IGiphyController
import com.waz.zclient.controllers.location.ILocationController
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.core.stores.network.{DefaultNetworkAction, INetworkStore}
import com.waz.zclient.media.SoundController
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.LayoutSpec
import com.waz.zclient.ui.cursor.{CursorMenuItem => JCursorMenuItem}
import com.waz.ZLog.ImplicitTag._

import concurrent.duration._

class CursorController(implicit inj: Injector, ctx: Context, evc: EventContext) extends Injectable {
  import CursorController._
  import Threading.Implicits.Ui

  val zms = inject[Signal[ZMessaging]]
  val conv = inject[Signal[ConversationData]]

  val keyboard = Signal[KeyboardState](KeyboardState.Hidden)
  val editingMsg = Signal(Option.empty[MessageData])

  val secondaryToolbarVisible = Signal(false)
  val enteredText = Signal("")
  val cursorWidth = Signal[Int]()
  val editHasFocus = Signal(false)
  var cursorCallback = Option.empty[CursorCallback]
  val onEditMessageReset = EventStream[Unit]()

  val extendedCursor = keyboard map {
    case KeyboardState.ExtendedCursor(tpe) => tpe
    case _ => ExtendedCursorContainer.Type.NONE
  }
  val selectedItem = extendedCursor map {
    case ExtendedCursorContainer.Type.IMAGES                 => Some(CursorMenuItem.Camera)
    case ExtendedCursorContainer.Type.VOICE_FILTER_RECORDING => Some(CursorMenuItem.AudioMessage)
    case _ => Option.empty[CursorMenuItem]
  }
  val isEditingMessage = editingMsg.map(_.isDefined)
  val ephemeralSelected = extendedCursor.map(_ == ExtendedCursorContainer.Type.EPHEMERAL)
  val emojiKeyboardVisible = extendedCursor.map(_ == ExtendedCursorContainer.Type.EMOJIS)
  val convIsEphemeral = conv.map(_.ephemeral != EphemeralExpiration.NONE)

  val convIsActive = conv.map(_.isActive)
  val isEphemeralMode = convIsEphemeral.zip(ephemeralSelected) map { case (ephConv, selected) => ephConv || selected }

  val onCursorItemClick = EventStream[CursorMenuItem]()

  val onMessageSent = EventStream[MessageData]()
  val onMessageEdited = EventStream[MessageData]()
  val onEphemeralExpirationSelected = EventStream[ConversationData]()

  val sendButtonEnabled: Signal[Boolean] = zms.map(_.userPrefs).flatMap(_.preference(UserPreferences.SendButtonEnabled).signal)

  val enteredTextEmpty = enteredText.map(_.trim.isEmpty).orElse(Signal const true)
  val sendButtonVisible = Signal(emojiKeyboardVisible, enteredTextEmpty, sendButtonEnabled, isEditingMessage) map {
    case (emoji, empty, enabled, editing) => enabled && (emoji || !empty) && !editing
  }
  val ephemeralBtnVisible = Signal(isEditingMessage, convIsActive, enteredTextEmpty, sendButtonVisible) map {
    case (false, true, true, false) => true
    case _ => false
  }

  val onShowTooltip = EventStream[(CursorMenuItem, View)]   // (item, anchor)

  private val actionsController = inject[MessageActionsController]

  actionsController.onMessageAction {
    case (MessageAction.Edit, message) =>
      editingMsg ! Some(message)
      CancellableFuture.delayed(100.millis) { keyboard ! KeyboardState.Shown }
    case _ =>
      // ignore
  }

  // notify SE about typing state
  enteredText { text =>
    for {
      typing <- zms.map(_.typing).head
      convId <- conv.map(_.id).head
    } {
      if (text.isEmpty) typing.selfClearedInput(convId)
      else typing.selfChangedInput(convId)
    }
  }

  val typingIndicatorVisible = for {
    typing <- zms.map(_.typing)
    convId <- conv.map(_.id)
    users <- typing.typingUsers(convId)
  } yield
    users.nonEmpty

  keyboard.on(Threading.Ui) {
    case KeyboardState.Shown =>
      cursorCallback.foreach(_.hideExtendedCursor())
      KeyboardUtils.showKeyboard(activity)
    case KeyboardState.Hidden =>
      cursorCallback.foreach(_.hideExtendedCursor())
      KeyboardUtils.closeKeyboardIfShown(activity)
    case KeyboardState.ExtendedCursor(tpe) =>
      KeyboardUtils.closeKeyboardIfShown(activity)

      if (LayoutSpec.isTablet(ctx) && tpe == ExtendedCursorContainer.Type.IMAGES) {
        cameraController.openCamera(CameraContext.MESSAGE)
        keyboard ! KeyboardState.Hidden
      } else {
        permissions.withPermissions(keyboardPermissions(tpe): _*) {
          cursorCallback.foreach(_.openExtendedCursor(tpe))
        }
      }
  }

  editHasFocus {
    case true => cursorCallback.foreach(_.onFocusChange(true))
    case false => // ignore
  }

  def submit(msg: String): Boolean = {
    if (isEditingMessage.currentValue.contains(true)) {
      onApproveEditMessage()
      return true
    }
    if (TextUtils.isEmpty(msg.trim)) return false

    for {
      c <- conv.head.map(_.id)
      cs <- zms.head.map(_.convsUi)
      m <- cs.sendMessage(c, new MessageContent.Text(msg))
    } {
      m foreach { msg =>
        onMessageSent ! msg
        cursorCallback.foreach(_.onMessageSent(msg))
      }
    }

    true
  }

  def onApproveEditMessage(): Unit =
    for {
      c <- conv.head
      cs <- zms.head.map(_.convsUi)
      m <- editingMsg.head if m.isDefined
      msg = m.get
      text <- enteredText.head
    } {
      if (text.trim().isEmpty) {
        cs.recallMessage(c.id, msg.id)
        Toast.makeText(ctx, R.string.conversation__message_action__delete__confirmation, Toast.LENGTH_SHORT).show()
      } else {
        cs.updateMessage(c.id, msg.id, new MessageContent.Text(text))
      }
      editingMsg ! None
      keyboard ! KeyboardState.Hidden
    }

  lazy val userPreferences = inject[IUserPreferencesController]

  def toggleEphemeralMode() = {
    val lastExpiraton = EphemeralExpiration.getForMillis(userPreferences.getLastEphemeralValue)
    if (lastExpiraton != EphemeralExpiration.NONE) {
      conv.head foreach { c =>
        zms.head.flatMap { _.convsUi.setEphemeral(c.id, if (c.ephemeral == EphemeralExpiration.NONE) lastExpiraton else EphemeralExpiration.NONE) } foreach {
          case Some((prev, current)) if prev.ephemeral == EphemeralExpiration.NONE =>
            onEphemeralExpirationSelected ! current
          case _ => // ignore
        }
      }
      keyboard mutate {
        case KeyboardState.ExtendedCursor(_) => KeyboardState.Hidden
        case state => state
      }
    }
  }

  lazy val drawingController = inject[IDrawingController]
  lazy val giphyController = inject[IGiphyController]
  lazy val cameraController = inject[ICameraController]
  lazy val locationController = inject[ILocationController]
  lazy val soundController = inject[SoundController]
  lazy val permissions = inject[PermissionsController]
  lazy val networkStore = inject[INetworkStore]
  lazy val activity = inject[Activity]

  import CursorMenuItem._
  onCursorItemClick {
    case CursorMenuItem.More => secondaryToolbarVisible ! true
    case CursorMenuItem.Less => secondaryToolbarVisible ! false
    case AudioMessage =>
        keyboard ! KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.VOICE_FILTER_RECORDING)
    case Camera =>
        keyboard ! KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.IMAGES)
    case Ping =>
      networkStore.doIfHasInternetOrNotifyUser(new DefaultNetworkAction() {
        override def execute(networkMode: NetworkMode): Unit = for {
          z <- zms.head
          c <- conv.head
          _ <- z.convsUi.knock(c.id)
        } {
          soundController.playPingFromMe()
        }
      })
    case Sketch =>
      drawingController.showDrawing(null, IDrawingController.DrawingDestination.SKETCH_BUTTON)
    case File =>
      cursorCallback.foreach(_.openFileSharing())
    case VideoMessage =>
      cursorCallback.foreach(_.captureVideo())
    case Location =>
      val googleAPI = GoogleApiAvailability.getInstance
      if (ConnectionResult.SUCCESS == googleAPI.isGooglePlayServicesAvailable(ctx)) {
        KeyboardUtils.hideKeyboard(activity)
        locationController.showShareLocation()
      }
      else Toast.makeText(ctx, R.string.location_sharing__missing_play_services, Toast.LENGTH_LONG).show()
    case Gif =>
      enteredText.head foreach { giphyController.handleInput }
    case Send =>
      enteredText.head foreach { submit }
    case _ =>
      // ignore
  }
}

object CursorController {

  sealed trait KeyboardState
  object KeyboardState {
    case object Hidden extends KeyboardState
    case object Shown extends KeyboardState
    case class ExtendedCursor(tpe: ExtendedCursorContainer.Type) extends KeyboardState
  }

  val KeyboardPermissions = Map(
    ExtendedCursorContainer.Type.IMAGES -> Seq(CameraPermission, ReadExternalStoragePermission),
    ExtendedCursorContainer.Type.VOICE_FILTER_RECORDING -> Seq(RecordAudioPermission)
  )

  def keyboardPermissions(tpe: ExtendedCursorContainer.Type) = KeyboardPermissions.getOrElse(tpe, Seq.empty)
}

// temporary for compatibility with ConversationFragment
trait CursorCallback {
  def openExtendedCursor(tpe: ExtendedCursorContainer.Type): Unit
  def hideExtendedCursor(): Unit
  def openFileSharing(): Unit
  def captureVideo(): Unit

  def onMessageSent(msg: MessageData): Unit
  def onCursorButtonLongPressed(cursorMenuItem: JCursorMenuItem): Unit
  def onMotionEventFromCursorButton(cursorMenuItem: JCursorMenuItem, motionEvent: MotionEvent): Unit
  def onFocusChange(hasFocus: Boolean): Unit
  def onCursorClicked(): Unit
}
