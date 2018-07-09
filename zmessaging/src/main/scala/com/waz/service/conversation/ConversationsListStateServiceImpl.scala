/*
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service.conversation

import com.waz.ZLog._
import com.waz.content.UserPreferences.SelectedConvId
import com.waz.content.{ConversationStorage, Preferences, UserPreferences}
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConvId, ConversationData, RemoteInstant}
import com.waz.threading.SerialDispatchQueue
import com.waz.utils._
import com.waz.utils.events.Signal
import org.threeten.bp.Instant

import scala.concurrent.Future

trait ConversationsListStateService {
  def selectedConvIdPref: Preferences.Preference[Option[ConvId]]
  def selectedConversationId: Signal[Option[ConvId]]
  def selectConversation(id: Option[ConvId]): Future[Unit]
}

/**
 * Keeps track of general conversation list stats needed for display of conversations lists.
 */
class ConversationsListStateServiceImpl(convs: ConversationStorage, userPrefs: UserPreferences) extends ConversationsListStateService {

  import com.waz.utils.events.EventContext.Implicits.global

  implicit val dispatcher = new SerialDispatchQueue(name = "ConversationsListStateService")

  private[conversation] case class ConversationListStats(unreadCount: Int = 0, unsentCount: Int = 0, pendingCount: Int = 0, selectedConversationId: Option[ConvId] = None)

  val selectedConvIdPref: Preferences.Preference[Option[ConvId]] = userPrefs.preference(SelectedConvId)
  private[conversation] val listStats = Signal[ConversationListStats](ConversationListStats())

  private[conversation] var lastEventTime = RemoteInstant.Epoch

  def selectedConversationId: Signal[Option[ConvId]] = selectedConvIdPref.signal

  convs.convAdded.on(dispatcher) { onAdded }
  convs.convDeleted.on(dispatcher) { onDeleted }
  convs.convUpdated.on(dispatcher) { case (prev, updated) => onDeleted(prev); onAdded(updated) }

  convs.list .map { cs =>
    cs foreach onAdded
  } .recoverWithLog(reportHockey = true)(logTagFor[ConversationsListStateServiceImpl])

  private def onAdded(c: ConversationData) = if (!c.archived) {
    addToCounts(c, +1)
    if (c.lastEventTime.isAfter(lastEventTime)) lastEventTime = c.lastEventTime
  }

  private def onDeleted(c: ConversationData) = if (!c.archived) addToCounts(c, -1)

  private def addToCounts(c: ConversationData, sign: Int): Unit = listStats mutate { stats =>
    val sgn = math.signum(sign)
    stats.copy(
      unreadCount = stats.unreadCount + sgn * c.unreadCount.messages,
      unsentCount = stats.unsentCount + sgn * c.failedCount,
      pendingCount = stats.pendingCount + (if (c.convType == ConversationType.WaitForConnection) sgn else 0)
    )
  }

  def selectConversation(id: Option[ConvId]): Future[Unit] = selectedConvIdPref := id
}
