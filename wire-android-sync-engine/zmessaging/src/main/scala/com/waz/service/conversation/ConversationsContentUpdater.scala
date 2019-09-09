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

import com.waz.log.LogSE._
import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.content._
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.sync.ReceiptType
import com.waz.model.{UserId, _}
import com.waz.service.tracking.TrackingService
import com.waz.sync.SyncServiceHandle
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

trait ConversationsContentUpdater {
  def convById(id: ConvId): Future[Option[ConversationData]]
  def convByRemoteId(id: RConvId): Future[Option[ConversationData]]
  def convsByRemoteId(ids: Set[RConvId]): Future[Map[RConvId, ConversationData]]
  def storage: ConversationStorage
  def updateConversation(id: ConvId, convType: Option[ConversationType] = None, hidden: Option[Boolean] = None): Future[Option[(ConversationData, ConversationData)]]
  def hideIncomingConversation(user: UserId): Future[Option[(ConversationData, ConversationData)]]
  def setConversationHidden(id: ConvId, hidden: Boolean): Future[Option[(ConversationData, ConversationData)]]
  def processConvWithRemoteId[A](remoteId: RConvId, retryAsync: Boolean, retryCount: Int = 0)(processor: ConversationData => Future[A])(implicit tag: LogTag, ec: ExecutionContext): Future[A]
  def updateConversationLastRead(id: ConvId, time: RemoteInstant): Future[Option[(ConversationData, ConversationData)]]
  def updateConversationMuted(conv: ConvId, muted: MuteSet): Future[Option[(ConversationData, ConversationData)]]
  def updateConversationName(id: ConvId, name: Name): Future[Option[(ConversationData, ConversationData)]]
  def setConvActive(id: ConvId, active: Boolean): Future[Unit]
  def updateConversationArchived(id: ConvId, archived: Boolean): Future[Option[(ConversationData, ConversationData)]]
  def updateConversationCleared(id: ConvId, time: RemoteInstant): Future[Option[(ConversationData, ConversationData)]]
  def updateReceiptMode(id: ConvId, receiptMode: Int): Future[Option[(ConversationData, ConversationData)]]
  def updateLastEvent(id: ConvId, time: RemoteInstant): Future[Option[(ConversationData, ConversationData)]]
  def updateConversationState(id: ConvId, state: ConversationState): Future[Option[(ConversationData, ConversationData)]]
  def updateAccessMode(id: ConvId, access: Set[Access], accessRole: Option[AccessRole], link: Option[ConversationData.Link] = None): Future[Option[(ConversationData, ConversationData)]]

  def createConversationWithMembers(convId: ConvId,
                                    remoteId: RConvId,
                                    convType: ConversationType,
                                    creator: UserId,
                                    members: Set[UserId],
                                    name: Option[Name] = None,
                                    hidden: Boolean = false,
                                    access: Set[Access] = Set(Access.PRIVATE),
                                    accessRole: AccessRole = AccessRole.PRIVATE,
                                    receiptMode: Int = 0): Future[ConversationData]
}

class ConversationsContentUpdaterImpl(val storage:     ConversationStorage,
                                      selfUserId:      UserId,
                                      teamId:          Option[TeamId],
                                      usersStorage:    UsersStorage,
                                      userPrefs:       UserPreferences,
                                      membersStorage:  MembersStorage,
                                      messagesStorage: => MessagesStorage,
                                      tracking:        TrackingService,
                                      syncHandler:     SyncServiceHandle) extends ConversationsContentUpdater with DerivedLogTag {
  import com.waz.utils.events.EventContext.Implicits.global

  private implicit val dispatcher = new SerialDispatchQueue(name = "ConversationContentUpdater")

  val conversationsFuture = Future successful storage

  storage.onUpdated(_.foreach {
    case (prev, conv) if prev.cleared != conv.cleared =>
      verbose(l"cleared updated will clear messages, prev: $prev, updated: $conv")
      conv.cleared.foreach(messagesStorage.clear(conv.id, _).recoverWithLog())
    case _ =>
  })

  private val shouldFixDuplicatedConversations = userPrefs.preference(UserPreferences.FixDuplicatedConversations)

  for {
    shouldFix <- shouldFixDuplicatedConversations()
    _         <- if (shouldFix) fixDuplicatedConversations() else Future.successful({})
    _         <- if (shouldFix) shouldFixDuplicatedConversations := false else Future.successful({})
  } yield {}

  private val shouldCheckMutedStatus = userPrefs.preference(UserPreferences.CheckMutedStatus)

  for {
    shouldCheck <- shouldCheckMutedStatus()
    _ <- if (shouldCheck) checkMutedStatus() else Future.successful({})
    _ <- if (shouldCheck) shouldCheckMutedStatus := false else Future.successful({})
  } yield {}

  override def convById(id: ConvId): Future[Option[ConversationData]] = storage.get(id)

  override def convByRemoteId(id: RConvId): Future[Option[ConversationData]] = storage.getByRemoteId(id)

  override def convsByRemoteId(ids: Set[RConvId]): Future[Map[RConvId, ConversationData]] =
    storage.getByRemoteIds2(ids)

  override def updateConversationName(id: ConvId, name: Name) = storage.update(id, { conv =>
      if (conv.convType == ConversationType.Group)
        conv.copy(name = if (name.isEmpty) None else Some(name))
      else
        conv
    })

  override def setConvActive(id: ConvId, active: Boolean) = storage.update(id, { _.copy(isActive = active)}).map(_ => {})

  override def updateConversationArchived(id: ConvId, archived: Boolean) = storage.update(id, { c =>
    c.copy(archived = archived, archiveTime = c.lastEventTime)
  })

  override def updateConversationMuted(conv: ConvId, muted: MuteSet) = storage.update(conv, { c =>
    verbose(l"updateConversationMuted($conv, $muted), muteTime = ${c.lastEventTime}")
    c.copy(muteTime = c.lastEventTime, muted = muted)
  })

  override def updateConversationLastRead(id: ConvId, time: RemoteInstant) = storage.update(id, { conv =>
    verbose(l"updateConversationLastRead($id, $time)")
    conv.withLastRead(time)
  })

  override def updateConversationCleared(id: ConvId, time: RemoteInstant) = storage.update(id, { conv =>
    verbose(l"updateConversationCleared($id, $time)")
    conv.withCleared(time).withLastRead(time)
  })

  override def updateReceiptMode(id: ConvId, receiptMode: Int): Future[Option[(ConversationData, ConversationData)]] = storage.update(id, { c =>
    c.copy(receiptMode = Some(receiptMode))
  })

  override def updateConversationState(id: ConvId, state: ConversationState) = storage.update(id, { conv =>
    verbose(l"updateConversationState($conv, state: $state)")

    val (archived, archiveTime) = state match {
      case ConversationState(Some(a), Some(t), _, _, _) if t >= conv.archiveTime => (a, t)
      case _ => (conv.archived, conv.archiveTime)
    }

    val (muteTime, muteSet) = state match {
      case ConversationState(_, _, _, Some(t), _) if t >= conv.muteTime => (t, MuteSet.resolveMuted(state, teamId.isDefined))
      case _ => (conv.muteTime, conv.muted)
    }

    conv.copy(archived = archived, archiveTime = archiveTime, muteTime = muteTime, muted = muteSet)
  })

  override def updateLastEvent(id: ConvId, time: RemoteInstant) = storage.update(id, { conv =>
    verbose(l"updateLastEvent($conv, $time)")
    if (conv.lastEventTime.isAfter(time)) conv
    else {
      debug(l"updating: $conv, lastEventTime: $time")
      conv.copy(lastEventTime = conv.lastEventTime max time)
    }
  })

  override def updateConversation(id: ConvId, convType: Option[ConversationType] = None, hidden: Option[Boolean] = None) =
    storage.update(id, { conv =>
      if (convType.forall(_ == conv.convType) && hidden.forall(_ == conv.hidden)) conv
      else conv.copy(convType = convType.getOrElse(conv.convType), hidden = hidden.getOrElse(conv.hidden))
    })

  override def setConversationHidden(id: ConvId, hidden: Boolean) = storage.update(id, _.copy(hidden = hidden))

  override def createConversationWithMembers(convId: ConvId,
                                             remoteId: RConvId,
                                             convType: ConversationType,
                                             creator: UserId,
                                             members: Set[UserId],
                                             name: Option[Name] = None,
                                             hidden: Boolean = false,
                                             access: Set[Access] = Set(Access.PRIVATE),
                                             accessRole: AccessRole = AccessRole.PRIVATE,
                                             receiptMode: Int = 0): Future[ConversationData] = {
    for {
      users <- usersStorage.listAll(members.toSeq)
      conv <- storage.insert(
        ConversationData(
          convId,
          remoteId,
          name          = name,
          creator       = creator,
          convType      = convType,
          generatedName = NameUpdater.generatedName(convType)(users),
          hidden        = hidden,
          team          = teamId,
          access        = access,
          accessRole    = Some(accessRole),
          receiptMode   = Some(receiptMode)
        ))
      _ <- membersStorage.add(convId, members + creator)
    } yield conv
  }

  override def hideIncomingConversation(user: UserId) = storage.update(ConvId(user.str), { conv =>
    if (conv.convType == ConversationType.Incoming) conv.copy(hidden = true) else conv
  })

  /**
   * Helper for event processing. Can be used whenever some event processing needs to access conversation by remoteId,
   * but conversation may not be present yet, for example if events are processed in wrong order.
   * Processing will be retried with delay.
   *
   * @param retryAsync - true if retry should be executed asynchronously, this should be used when executing from some ProcessingQueue, using delays in processing queue will block it
   */
  override def processConvWithRemoteId[A](remoteId: RConvId, retryAsync: Boolean, retryCount: Int = 0)(processor: ConversationData => Future[A])(implicit tag: LogTag, ec: ExecutionContext): Future[A] = {

    def retry() = CancellableFuture.delay(ConversationsService.RetryBackoff.delay(retryCount)).future .flatMap { _ =>
      processConvWithRemoteId(remoteId, retryAsync = false, retryCount + 1)(processor)(tag, ec)
    } (ec)

    convByRemoteId(remoteId) .flatMap {
      case Some(conv) => processor(conv)
      case None if retryCount > 3 =>
        val ex = new NoSuchElementException("No conversation data found") with NoStackTrace
        tracking.exception(ex, "No conversation data found")(tag)
        Future.failed(ex)
      case None =>
        warn(l"No conversation data found for remote id: $remoteId on try: $retryCount")(tag)
        if (retryAsync) {
          retry()
          Future.failed(new NoSuchElementException(s"No conversation data found for: $remoteId"))
        } else
          retry()
    } (ec)
  }

  override def updateAccessMode(id: ConvId, access: Set[Access], accessRole: Option[AccessRole], link: Option[ConversationData.Link] = None) =
    storage.update(id, conv => conv.copy(access = access, accessRole = accessRole, link = if (!access.contains(Access.CODE)) None else link.orElse(conv.link)))


  private def fixDuplicatedConversations(): Future[Unit] = {

    def moveMessages(from: ConvId, to: ConvId): Future[Unit] =
      for {
        messages <- messagesStorage.findMessagesFrom(from, RemoteInstant.Epoch)
        _        <- messagesStorage.updateAll2(messages.map(_.id), { m => m.copy(convId = to) })
      } yield ()

    for {
      convs      <- storage.list()
      duplicates =  convs.groupBy(_.remoteId).filter(_._2.size > 1).map(_._2.map(_.id))
      convIds    =  duplicates.flatten.toSet
      users      <- usersStorage.listAll(convIds.map(id => UserId(id.str)))
      userIdSet  =  users.map(_.id).toSet
      isOriginal =  convIds.map(id => id -> userIdSet.contains(UserId(id.str))).toMap
      pairs      =  duplicates.flatMap { pair =>
        val (original, updates) = pair.partition(isOriginal)
        (original.headOption, updates) match {
          // there should be exactly one original and one or more duplicates
          case (Some(orig), upd) if upd.nonEmpty => Some(orig, upd)
          case _ => None
        }
      }.toMap
      _ = verbose(l"fixDuplicatedConversations: (original, updates) pairs: $pairs")
      _ <- Future.sequence(pairs.map { case (origId, updates) =>
        for {
          _ <- Future.sequence(updates.map(updId => moveMessages(updId, origId)))
          _ <- if (updates.tail.nonEmpty) storage.removeAll(updates.tail) else Future.successful({})
          _ <- storage.updateLocalId(updates.head, origId)
          _ <- Future.sequence(updates.map(membersStorage.delete))
          _ <- updateConversation(origId, Some(ConversationType.OneToOne), hidden = Some(false))
          _ <- membersStorage.add(origId, Seq(selfUserId, UserId(origId.str)))
        } yield ()
      })
    } yield ()
  }
  
  private def checkMutedStatus(): Future[Unit] =
    if (teamId.nonEmpty) {
      Future.successful({})
    } else
      for {
        convs        <- storage.list()
        mentionsOnly =  convs.filter(_.onlyMentionsAllowed).map(_.id)
        _            <- storage.updateAll2(mentionsOnly, _.copy(muted = MuteSet.AllMuted))
        _            <- syncHandler.syncConversations()
      } yield {}
}
