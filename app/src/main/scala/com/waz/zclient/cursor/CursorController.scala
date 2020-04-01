/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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

import android.Manifest.permission.{CAMERA, READ_EXTERNAL_STORAGE, RECORD_AUDIO, WRITE_EXTERNAL_STORAGE}
import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.view.{MotionEvent, View}
import android.widget.Toast
import com.google.android.gms.common.{ConnectionResult, GoogleApiAvailability}
import com.waz.api.NetworkMode
import com.waz.content.GlobalPreferences.IncognitoKeyboardEnabled
import com.waz.content.{GlobalPreferences, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service.{NetworkModeService, ZMessaging}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.common.controllers._
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.controllers.location.ILocationController
import com.waz.zclient.conversation.{ConversationController, ReplyController}
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.drawing.DrawingFragment
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.ui.cursor.{CursorMenuItem => JCursorMenuItem}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.views.DraftMap
import com.waz.zclient.{Injectable, Injector, R}
import com.waz.zclient.BuildConfig

import scala.collection.immutable.ListSet
import scala.concurrent.Future
import scala.concurrent.duration._

class CursorController(implicit inj: Injector, ctx: Context, evc: EventContext)
  extends Injectable with DerivedLogTag {

  import CursorController._
  import Threading.Implicits.Ui

  private lazy val zms                     = inject[Signal[ZMessaging]]
  private lazy val conversationController  = inject[ConversationController]
  private lazy val convListController      = inject[ConversationListController]
  private lazy val callController          = inject[CallController]
  private lazy val replyController         = inject[ReplyController]
  private lazy val userPrefs               = inject[Signal[UserPreferences]]

  val conv = conversationController.currentConv

  val keyboard = Signal[KeyboardState](KeyboardState.Hidden)
  val editingMsg = Signal(Option.empty[MessageData])

  val secondaryToolbarVisible = Signal(false)
  val enteredText = Signal[(CursorText, EnteredTextSource)]((CursorText.Empty, EnteredTextSource.FromController))
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

  val ephemeralExp = conv.map(_.ephemeralExpiration)
  val isEphemeral  = ephemeralExp.map(_.isDefined)

  val emojiKeyboardVisible = extendedCursor.map(_ == ExtendedCursorContainer.Type.EMOJIS)
  val convAvailability = for {
    convId <- conv.map(_.id)
    av <- convListController.availability(convId)
  } yield av

  val convIsActive = conv.map(_.isActive)

  val onCursorItemClick = EventStream[CursorMenuItem]()

  val onMessageSent = EventStream[MessageData]()
  val onMessageEdited = EventStream[MessageData]()
  val onEphemeralExpirationSelected = EventStream[Option[FiniteDuration]]()

  val sendButtonEnabled: Signal[Boolean] = for {
    prefs    <- userPrefs
    sendPref <- prefs(UserPreferences.SendButtonEnabled).signal
    emoji    <- emojiKeyboardVisible
  } yield emoji || sendPref

  val keyboardPrivateMode =
    if(BuildConfig.FORCE_PRIVATE_KEYBOARD) Signal.const(true) else {
      for {
        prefs <- userPrefs
        mode  <- prefs(IncognitoKeyboardEnabled).signal
      } yield mode
    }

  val enteredTextEmpty = enteredText.map(_._1.isEmpty).orElse(Signal const true)
  val sendButtonVisible = Signal(emojiKeyboardVisible, enteredTextEmpty, sendButtonEnabled, isEditingMessage) map {
    case (emoji, empty, enabled, editing) => enabled && (emoji || !empty) && !editing
  }
  val ephemeralBtnVisible = Signal(isEditingMessage, convIsActive).flatMap {
    case (false, true) =>
      isEphemeral.flatMap {
        case true => Signal.const(true)
        case _ => sendButtonVisible.map(!_)
      }
    case _ => Signal.const(false)
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
  private var prevEnteredText = ""
  enteredText {
    case (CursorText(text, _), EnteredTextSource.FromView) if text != prevEnteredText =>
      for {
        typing <- zms.map(_.typing).head
        convId <- conversationController.currentConvId.head
      } {
        if (!text.isEmpty) typing.selfChangedInput(convId)
        else typing.selfClearedInput(convId)
      }
      prevEnteredText = text
    case _ =>
  }

  val typingIndicatorVisible = for {
    typing <- zms.map(_.typing)
    convId <- conversationController.currentConvId
    users <- typing.typingUsers(convId)
  } yield
    users.nonEmpty

  def notifyKeyboardVisibilityChanged(keyboardIsVisible: Boolean): Unit = {
    keyboard.mutate {
      case KeyboardState.Shown if !keyboardIsVisible => KeyboardState.Hidden
      case _ if keyboardIsVisible => KeyboardState.Shown
      case state => state
    }

    if (keyboardIsVisible) editHasFocus.currentValue.foreach { hasFocus =>
      if (hasFocus) {
        cursorCallback.foreach(_.onCursorClicked())
      }
    }
  }

  keyboard.on(Threading.Ui) {
    case KeyboardState.Shown =>
      cursorCallback.foreach(_.hideExtendedCursor())
      KeyboardUtils.showKeyboard(activity)
    case KeyboardState.Hidden =>
      cursorCallback.foreach(_.hideExtendedCursor())
      KeyboardUtils.closeKeyboardIfShown(activity)
    case KeyboardState.ExtendedCursor(tpe) =>
      KeyboardUtils.closeKeyboardIfShown(activity)

      permissions.requestAllPermissions(keyboardPermissions(tpe)).map {
        case true => cursorCallback.foreach(_.openExtendedCursor(tpe))
        case _ =>
          //TODO error message?
          keyboard ! KeyboardState.Hidden
      } (Threading.Ui)
  }

  screenController.hideGiphy.onUi {
    case true =>
      // giphy worked, so no need for the draft text to reappear
      inject[DraftMap].resetCurrent().map { _ =>
        enteredText ! (CursorText.Empty, EnteredTextSource.FromController)
      }
    case false =>
  }

  editHasFocus {
    case true => // TODO - reimplement for tablets
    case false => // ignore
  }

  private val msgBeingSendInConv = Signal(Set.empty[ConvId])

  def submit(msg: String, mentions: Seq[Mention] = Nil): Boolean = {
    if (isEditingMessage.currentValue.contains(true)) {
      onApproveEditMessage()
      true
    }
    else if (TextUtils.isEmpty(msg.trim)) false
    else {
      (for {
        convId <- conv.map(_.id).head
        inMBS  =  msgBeingSendInConv.map(_.contains(convId)).currentValue
        quote  <- replyController.currentReplyContent.map(_.map(_.message.id)).head
      } yield (convId, quote, inMBS)).foreach {
        case (convId, quote, Some(false))  =>
          msgBeingSendInConv.mutate(_ + convId)
          conversationController.sendMessage(msg, mentions, quote).foreach { m =>
            m.foreach { msg =>
              onMessageSent ! msg
              cursorCallback.foreach(_.onMessageSent(msg))
              replyController.clearMessage(msg.convId)

              Future {
                msgBeingSendInConv.mutate(_ - msg.convId)
              }
            }
          }
        case _ =>
      }
      true
    }
  }

  def onApproveEditMessage(): Unit =
    for {
      cId <- conversationController.currentConvId.head
      cs  <- zms.head.map(_.convsUi)
      m   <- editingMsg.head if m.isDefined
      msg = m.get
      (CursorText(text, mentions), _) <- enteredText.head
    } {
      if (text.trim.isEmpty) {
        cs.recallMessage(cId, msg.id)
        Toast.makeText(ctx, R.string.conversation__message_action__delete__confirmation, Toast.LENGTH_SHORT).show()
      } else {
        cs.updateMessage(cId, msg.id, text, mentions)
      }
      editingMsg ! None
      keyboard ! KeyboardState.Hidden
    }

  private val lastEphemeralValue = inject[GlobalPreferences].preference(GlobalPreferences.LastEphemeralValue).signal

  def toggleEphemeralMode(): Unit =
    for {
      lastExpiration <- lastEphemeralValue.head
      c              <- conv.head
      z              <- zms.head
      eph            = c.ephemeralExpiration
    } yield {
      if (lastExpiration.isDefined && (eph.isEmpty || !eph.get.isInstanceOf[ConvExpiry])) {
        val current = if (eph.isEmpty) lastExpiration else None
        z.convsUi.setEphemeral(c.id, current)
        if (eph != lastExpiration) onEphemeralExpirationSelected ! current
        keyboard mutate {
          case KeyboardState.ExtendedCursor(_) => KeyboardState.Hidden
          case state => state
        }
      }
    }

  private lazy val locationController = inject[ILocationController]
  private lazy val soundController    = inject[SoundController]
  private lazy val permissions        = inject[PermissionsService]
  private lazy val activity           = inject[Activity]
  private lazy val screenController   = inject[ScreenController]
  private lazy val accentColorController  = inject[AccentColorController]


  import CursorMenuItem._

  onCursorItemClick {
    case CursorMenuItem.More => secondaryToolbarVisible ! true
    case CursorMenuItem.Less => secondaryToolbarVisible ! false
    case AudioMessage =>
      checkIfCalling(isVideoMessage = false)(keyboard ! KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.VOICE_FILTER_RECORDING))
    case Camera =>
        keyboard ! KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.IMAGES)
    case Ping =>
      for {
        true <- inject[NetworkModeService].networkMode.map(m => m != NetworkMode.OFFLINE && m != NetworkMode.UNKNOWN).head
        z    <- zms.head
        cId  <- conversationController.currentConvId.head
        _    <- z.convsUi.knock(cId)
      } soundController.playPingFromMe()
    case Sketch =>
      screenController.showSketch ! DrawingFragment.Sketch.BlankSketch
    case File =>
      cursorCallback.foreach(_.openFileSharing())
    case VideoMessage =>
      checkIfCalling(isVideoMessage = true)(cursorCallback.foreach(_.captureVideo()))
    case Location =>
      showLocationIfAllowed()
    case Gif =>
      enteredText.head.foreach { _ => screenController.showGiphy ! Some(s"") }
    case Send =>
      enteredText.head.foreach { case (CursorText(text, mentions), _) => submit(text, mentions) }
    case _ =>
      // ignore
  }

  /**
    * Display location dialog, if Google Play Services are available, and the user confirmed
    * the permission to access location
    */
  private def showLocationIfAllowed(): Unit = {
    for {
      color <- accentColorController.accentColor.head

      // Check if the user was asked before
      preferences <- userPrefs.head
      askedForLocationPermissionPreference = preferences.preference(UserPreferences.AskedForLocationPermission)
      askedForLocation <- askedForLocationPermissionPreference.apply()

      // Show dialog only if the user didn't accept it before
      response <- if (!askedForLocation) ContextUtils.showConfirmationDialog(
        getString(R.string.location_sharing__permission__title),
        getString(R.string.location_sharing__permission__message),
        R.string.location_sharing__permission__continue,
        R.string.location_sharing__permission__cancel,
        color
      ) else { Future.successful(true) } // skip dialog
    } yield {
      if(response) { // user canceled
        // store that we asked and the user clicked "OK", so that we don't ask anymore
        // this is not a synchronous operation, but we are not interested in waiting
        askedForLocationPermissionPreference.update(true)

        val googleAPI = GoogleApiAvailability.getInstance
        if (ConnectionResult.SUCCESS == googleAPI.isGooglePlayServicesAvailable(ctx)) {
          KeyboardUtils.hideKeyboard(activity)
          locationController.showShareLocation()
        }
        else showToast(R.string.location_sharing__missing_play_services)
      }
    }
  }

  private def checkIfCalling(isVideoMessage: Boolean)(f: => Unit) =
    callController.isCallActive.head.foreach {
      case true  => showErrorDialog(R.string.calling_ongoing_call_title, if (isVideoMessage) R.string.calling_ongoing_call_video_message else R.string.calling_ongoing_call_audio_message)
      case false => f
    }
}

object CursorController {
  sealed trait EnteredTextSource
  object EnteredTextSource {
    case object FromView extends EnteredTextSource
    case object FromController extends EnteredTextSource
  }

  sealed trait KeyboardState
  object KeyboardState {
    case object Hidden extends KeyboardState
    case object Shown extends KeyboardState
    case class ExtendedCursor(tpe: ExtendedCursorContainer.Type) extends KeyboardState
  }

  val KeyboardPermissions = Map(
    ExtendedCursorContainer.Type.IMAGES -> ListSet(CAMERA, READ_EXTERNAL_STORAGE),
    ExtendedCursorContainer.Type.VOICE_FILTER_RECORDING -> ListSet(RECORD_AUDIO, WRITE_EXTERNAL_STORAGE)
  )

  def keyboardPermissions(tpe: ExtendedCursorContainer.Type): ListSet[PermissionsService.PermissionKey] = KeyboardPermissions.getOrElse(tpe, ListSet.empty)
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
  def onCursorClicked(): Unit
}
