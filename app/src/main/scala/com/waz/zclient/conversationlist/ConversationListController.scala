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
package com.waz.zclient.conversationlist

import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import com.waz.utils._
import com.waz.utils.events.{AggregatingSignal, EventContext, EventStream, Signal}
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.conversationlist.ConversationListAdapter.{Incoming, ListMode, Normal}
import com.waz.zclient.conversationlist.ConversationListManagerFragment.ConvListUpdateThrottling
import com.waz.zclient.conversationlist.views.ConversationAvatarView
import com.waz.zclient.utils.{UiStorage, UserSignal}
import com.waz.zclient.{Injectable, Injector}
import com.waz.ZLog.ImplicitTag._

import scala.collection.mutable
import scala.concurrent.Future

class ConversationListController(implicit inj: Injector, ec: EventContext) extends Injectable {

  import ConversationListController._

  val zms = inject[Signal[ZMessaging]]
  val membersCache = zms map { new MembersCache(_) }
  val lastMessageCache = zms map { new LastMessageCache(_) }

  def members(conv: ConvId) = membersCache.flatMap(_.apply(conv))

  def lastMessage(conv: ConvId) = lastMessageCache.flatMap(_.apply(conv))

  lazy val userAccountsController = inject[UserAccountsController]
  implicit val uiStorage = inject[UiStorage]

  // availability will be other than None only when it's a one-to-one conversation
  // (and the other user's availability is set to something else than None)
  def availability(conv: ConvId): Signal[Availability] = for {
    currentUser <- userAccountsController.currentUser
    isInTeam = currentUser.exists(_.teamId.nonEmpty)
    memberIds <- if (isInTeam) members(conv) else Signal.const(Seq.empty)
    otherUser <- if (memberIds.size == 1) userData(memberIds.headOption) else Signal.const(Option.empty[UserData])
  } yield {
    otherUser.fold[Availability](Availability.None)(_.availability)
  }

  private def userData(id: Option[UserId]) = id.fold2(Signal.const(Option.empty[UserData]), uid => UserSignal(uid).map(Option(_)))

  lazy val establishedConversations = for {
    z          <- zms
    convs      <- z.convsContent.conversationsSignal.throttle(ConvListUpdateThrottling )
  } yield convs.conversations.filter(EstablishedListFilter)

  def conversationListData(listMode: ListMode) = for {
    z             <- zms
    processing    <- z.push.processing
    if !processing
    conversations <- z.convsStorage.convsSignal
    incomingConvs = conversations.conversations.filter(Incoming.filter).toSeq
    members <- Signal.sequence(incomingConvs.map(c => z.membersStorage.activeMembers(c.id).map(_.find(_ != z.selfUserId))):_*)
  } yield {
    val regular = conversations.conversations
      .filter{ conversationData =>
        listMode.filter(conversationData)
      }
      .toSeq
      .sorted(listMode.sort)
    val incoming = if (listMode == Normal) (incomingConvs, members.flatten) else (Seq(), Seq())
    (z.accountId, regular, incoming)
  }

  def nextConversation(convId: ConvId): Future[Option[ConvId]] =
    conversationListData(Normal).head.map {
      case (_, regular, _) => regular.lift(regular.indexWhere(_.id == convId) + 1).map(_.id)
    } (Threading.Background)
}

object ConversationListController {

  lazy val RegularListFilter: (ConversationData => Boolean) = { c => Set(ConversationType.OneToOne, ConversationType.Group, ConversationType.WaitForConnection).contains(c.convType) && !c.hidden && !c.archived }
  lazy val IncomingListFilter: (ConversationData => Boolean) = { c => !c.hidden && !c.archived && c.convType == ConversationType.Incoming }
  lazy val ArchivedListFilter: (ConversationData => Boolean) = { c => Set(ConversationType.OneToOne, ConversationType.Group, ConversationType.Incoming, ConversationType.WaitForConnection).contains(c.convType) && !c.hidden && c.archived }
  lazy val EstablishedListFilter: (ConversationData => Boolean) = { c => RegularListFilter(c) && c.convType != ConversationType.WaitForConnection }
  lazy val EstablishedArchivedListFilter: (ConversationData => Boolean) = { c => ArchivedListFilter(c) && c.convType != ConversationType.WaitForConnection }
  lazy val IntegrationFilter: (ConversationData => Boolean) = { c => c.convType == ConversationType.Group && !c.hidden }

  // Maintains a short list of members for each conversation.
  // Only keeps up to 4 users other than self user, this list is to be used for avatar in conv list.
  // We keep this always in memory to avoid reloading members list for every list row view (caused performance issues)
  class MembersCache(zms: ZMessaging)(implicit inj: Injector, ec: EventContext) extends Injectable {
    private implicit val dispatcher = new SerialDispatchQueue(name = "MembersCache")

    private def entries(convMembers: Seq[ConversationMemberData]) =
      convMembers.groupBy(_.convId).map { case (convId, ms) =>
        val otherUsers = ms.collect { case ConversationMemberData(user, _) if user != zms.selfUserId => user }
        convId -> ConversationAvatarView.shuffle(otherUsers, convId).take(4)
      }

    val updatedEntries = EventStream.union(
      zms.membersStorage.onAdded.map(_.map(_.convId).toSet),
      zms.membersStorage.onDeleted.map(_.map(_._2).toSet)
    ) mapAsync { convs =>
      zms.membersStorage.getByConvs(convs) map entries
    }

    val members = new AggregatingSignal[Map[ConvId, Seq[UserId]], Map[ConvId, Seq[UserId]]](updatedEntries, zms.membersStorage.list() map entries, _ ++ _)

    def apply(conv: ConvId) : Signal[Seq[UserId]] = members.map(_.getOrElse(conv, Seq.empty[UserId]))
  }


  // Keeps last message for each conversation, this is needed because MessagesStorage is not
  // supposed to be used for multiple conversations at the same time, as it loads an index of all conv messages.
  // Using MessagesStorage with multiple/all conversations forces it to reload full msgs index on every conv switch.
  class LastMessageCache(zms: ZMessaging)(implicit inj: Injector, ec: EventContext) extends Injectable {

    private val cache = new mutable.HashMap[ConvId, Signal[Option[MessageData]]]

    private val changeEvents = zms.messagesStorage.onChanged map { msgs => msgs.groupBy(_.convId).mapValues(_.maxBy(_.time)) }

    private def updateEvents(conv: ConvId) = changeEvents.map(_.get(conv)).collect { case Some(m) => m }

    private def getLastMessage(conv: ConvId) = CancellableFuture.lift(zms.storage.db.read { MessageData.MessageDataDao.last(conv)(_) })

    def apply(conv: ConvId): Signal[Option[MessageData]] = cache.getOrElseUpdate(conv,
      new AggregatingSignal[MessageData, Option[MessageData]](updateEvents(conv), getLastMessage(conv), {
        case (res @ Some(last), update) if last.time.isAfter(update.time) => res
        case (_, update) => Some(update)
      }))
  }

}
