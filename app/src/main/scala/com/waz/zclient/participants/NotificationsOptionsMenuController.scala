/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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

package com.waz.zclient.participants
import android.content.Context
import com.waz.model.{ConvId, MuteSet}
import com.waz.utils.events.{EventContext, EventStream, Signal, SourceStream}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.{Injectable, Injector, R}
import com.waz.zclient.participants.OptionsMenuController.BaseMenuItem
import com.waz.zclient.utils.ContextUtils.getString

class NotificationsOptionsMenuController(convId: ConvId, fromConversationList: Boolean)(implicit injector: Injector, context: Context, ec: EventContext) extends OptionsMenuController with Injectable {
  import NotificationsOptionsMenuController._

  private val convController = inject[ConversationController]
  private val conversation = convController.conversationData(convId)

  override val title: Signal[Option[String]] =
    if (fromConversationList)
      conversation.map(_.map(_.displayName))
    else
      Signal.const(Some(getString(R.string.conversation__action__notifications_title)))
  override val optionItems: Signal[Seq[OptionsMenuController.MenuItem]] = Signal.const(Seq(Everything, OnlyMentions, Nothing))
  override val onMenuItemClicked: SourceStream[OptionsMenuController.MenuItem] = EventStream()
  override val selectedItems: Signal[Set[OptionsMenuController.MenuItem]] =
    conversation.collect {
      case Some(c) => Set(menuItem(c.muted))
    }

  onMenuItemClicked.map {
    case Everything   => MuteSet.AllAllowed
    case OnlyMentions => MuteSet.OnlyMentionsAllowed
    case _            => MuteSet.AllMuted
  }.onUi(m => convController.setMuted(convId, muted = m))
}

object NotificationsOptionsMenuController {
  object Everything   extends BaseMenuItem(R.string.conversation__action__notifications_everything, None)
  object OnlyMentions extends BaseMenuItem(R.string.conversation__action__notifications_mentions_and_replies, None)
  object Nothing      extends BaseMenuItem(R.string.conversation__action__notifications_nothing, None)

  def menuItem(muteSet: MuteSet): BaseMenuItem = muteSet match {
    case MuteSet.AllMuted            => Nothing
    case MuteSet.OnlyMentionsAllowed => OnlyMentions
    case _                           => Everything
  }
}
