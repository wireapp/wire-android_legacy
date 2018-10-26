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
package com.waz.zclient.participants
import android.content.Context
import com.waz.model.{ConvId, MuteSet}
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, EventStream, Signal, SourceStream}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.{Injectable, Injector, R}
import com.waz.zclient.participants.OptionsMenuController.BaseMenuItem
import com.waz.zclient.utils.ContextUtils.getString

class NotificationsOptionsMenuController(convId: ConvId, fromConversationList: Boolean)(implicit injector: Injector, context: Context, ec: EventContext) extends OptionsMenuController with Injectable {

  private val zms = inject[Signal[ZMessaging]]
  private val conversation = zms.flatMap(_.convsStorage.signal(convId))
  private val convController = inject[ConversationController]

  override val title: Signal[Option[String]] =
    if (fromConversationList)
      conversation.map(_.displayName).map(Some(_))
    else
      Signal.const(Some(getString(R.string.conversation__action__notifications_title)))
  override val optionItems: Signal[Seq[OptionsMenuController.MenuItem]] = Signal.const(Seq(Everything, OnlyMentions, Nothing))
  override val onMenuItemClicked: SourceStream[OptionsMenuController.MenuItem] = EventStream()
  override val selectedItems: Signal[Set[OptionsMenuController.MenuItem]] = conversation.map(_.muted).map {
    case MuteSet.AllMuted => Set(Nothing)
    case MuteSet.OnlyMentionsAllowed => Set(OnlyMentions)
    case _ => Set(Everything)
  }

  onMenuItemClicked.map {
    case Everything => MuteSet.AllAllowed
    case OnlyMentions => MuteSet.OnlyMentionsAllowed
    case _ => MuteSet.AllMuted
  }.onUi(m => convController.setMuted(convId, muted = m))
}

object Everything   extends BaseMenuItem(R.string.conversation__action__notifications_everything, None)
object OnlyMentions extends BaseMenuItem(R.string.conversation__action__notifications_mentions_only, None)
object Nothing      extends BaseMenuItem(R.string.conversation__action__notifications_nothing, None)

