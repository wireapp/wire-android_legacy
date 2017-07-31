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
package com.waz.zclient.controllers.global

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.IConversation
import com.waz.model.MessageId
import com.waz.service.ZMessaging
import com.waz.utils._
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.{Injectable, Injector}
import org.threeten.bp.Instant

import scala.concurrent.duration._

class SelectionController(implicit injector: Injector, ev: EventContext) extends Injectable { ctrl =>
  import SelectionController._

  val zms = inject[Signal[ZMessaging]]

  val selectedConv = zms.flatMap(_.convsStats.selectedConversationId).collect { case Some(convId) => convId }

  private var previousConv = Option.empty[IConversation]
  private var currentConv  = Option.empty[IConversation]

  //TODO get rid of this as soon as possible
  private val selectedUiConv = for {
    z          <- zms
    cId        <- selectedConv
    Some(data) <- z.convsStorage.convsSignal.map(_.conversations.find(_.id == cId))
  } yield ZMessaging.currentUi.convs.getConversation(data)

  private var convListeners = Set.empty[ConversationChangedListener]

  //use sparingly!
  def setOnConversationChangeCallback(callback: ConversationChangedListener) = {
    convListeners += callback
    selectedUiConv.currentValue.foreach(current => callback.onConversationChanged(previousConv, Some(current)))
  }

  selectedUiConv.onUi { conv =>
    verbose(s"select Ui conv changed: prev: ${previousConv.map(_.getId).orNull}, current: ${conv.getId()}")
    if (!previousConv.contains(conv)) {
      previousConv = currentConv
      currentConv = Some(conv)
      convListeners.foreach(_.onConversationChanged(previousConv, currentConv))
    }

  }

  def selectedConversation = selectedUiConv.currentValue

  object messages {

    val ActivityTimeout = 3.seconds

    /**
      * Currently focused message.
      * There is only one focused message, switched by tapping.
      */
    val focused = Signal(Option.empty[MessageId])

    /**
      * Tracks last focused message together with last action time.
      * It's not cleared when message is unfocused, and toggleFocus takes timeout into account.
      * This is used to decide if timestamp view should be shown in footer when message has likes.
      */
    val lastActive = Signal((MessageId.Empty, Instant.EPOCH)) // message showing status info

    selectedConv.onChanged { c =>
      verbose(s"selected conv changed: $c")
      clear()
    }

    def clear() = {
      focused ! None
      lastActive ! (MessageId.Empty, Instant.EPOCH)
    }

    def isFocused(id: MessageId): Boolean = focused.currentValue.flatten.contains(id)

    /**
      * Switches current msg focus state to/from given msg.
      */
    def toggleFocused(id: MessageId) = {
      verbose(s"toggleFocused($id)")
      focused mutate {
        case Some(`id`) => None
        case _ => Some(id)
      }
      lastActive.mutate {
        case (`id`, t) if !ActivityTimeout.elapsedSince(t) => (id, Instant.now - ActivityTimeout)
        case _ => (id, Instant.now)
      }
    }
  }
}

object SelectionController {
  trait ConversationChangedListener {
    def onConversationChanged(prev: Option[IConversation], current: Option[IConversation]): Unit
  }
}
