/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.messages

import android.content.Context
import android.view.View
import com.waz.model.{ConvId, MessageData, MessageId}
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.controllers.global.SelectionController
import com.waz.zclient.controllers.navigation._
import com.waz.zclient.pages.main.conversationpager.controller.{ISlidingPaneController, SlidingPaneObserver}
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{Injectable, Injector}
import org.threeten.bp.Instant
import com.waz.ZLog.ImplicitTag._
import com.waz.api.Message

class MessagesController()(implicit injector: Injector, ev: EventContext) extends Injectable {
  import com.waz.threading.Threading.Implicits.Background

  val zms = inject[Signal[ZMessaging]]
  val context = inject[Context]
  val selectedConversation = inject[SelectionController].selectedConv
  val navigationController = inject[INavigationController]
  val slidingPaneController = inject[ISlidingPaneController]

  val scrolledToBottom = Signal(true)
  val onScrollToBottomRequested = EventStream[Int]

  val currentConvIndex = for {
    z       <- zms
    convId  <- selectedConversation
    index   <- Signal.future(z.messagesStorage.msgsIndex(convId))
  } yield
    index

  val lastMessage: Signal[MessageData] =
    currentConvIndex.flatMap { _.signals.lastMessage } map { _.getOrElse(MessageData.Empty) }

  val lastSelfMessage: Signal[MessageData] =
    currentConvIndex.flatMap { _.signals.lastMessageFromSelf } map { _.getOrElse(MessageData.Empty) }

  val uiActive = zms.flatMap { _.lifecycle.uiActive }

  // id of fully visible conv list, meaning that messages list for that conv is actually shown on screen (user sees messages)
  val fullyVisibleMessagesList: Signal[Option[ConvId]] = {
    val pageVisible = Signal[Boolean]()

    // XXX: This is a bit fragile. We are deducing signal state from loosely related events, and we rely on their order.
    navigationController.addNavigationControllerObserver(new NavigationControllerObserver {
      override def onPageVisible(page: Page) =
        pageVisible ! (page == Page.MESSAGE_STREAM || ViewUtils.isInLandscape(context.getResources.getConfiguration))
    })

    slidingPaneController.addObserver(new SlidingPaneObserver {
      override def onPanelClosed(panel: View) = ()
      override def onPanelSlide(panel: View, slideOffset: Float) = ()
      override def onPanelOpened(panel: View) = {
        pageVisible ! false
      }
    })

    uiActive flatMap {
      case false => Signal const Option.empty[ConvId]
      case true =>
        pageVisible flatMap {
          case true => selectedConversation.map(Some(_))
          case false => Signal const Option.empty[ConvId]
        }
    }
  }

  @volatile
  private var lastReadTime = Instant.EPOCH

  currentConvIndex.flatMap(_.signals.lastReadTime) { lastReadTime = _ }

  fullyVisibleMessagesList.disableAutowiring()

  def isLastSelf(id: MessageId) = lastSelfMessage.currentValue.exists(_.id == id)

  def onMessageRead(msg: MessageData) = fullyVisibleMessagesList.currentValue foreach {
    case Some(convId) if msg.convId == convId =>
      if (msg.isEphemeral && !msg.expired)
        zms.head foreach  { _.ephemeral.onMessageRead(msg.id) }

      if (msg.time isAfter lastReadTime)
        zms.head.foreach { _.convsUi.setLastRead(msg.convId, msg) }

      if (msg.state == Message.Status.FAILED)
        zms.head.foreach { _.messages.markMessageRead(convId, msg.id) }
    case _ =>
      // messages list is not visible, or not current conv, ignoring
  }

  def getMessage(messageId: MessageId): Signal[Option[MessageData]] = {
    zms.flatMap(z => Signal.future(z.messagesStorage.get(messageId)))
  }
}
