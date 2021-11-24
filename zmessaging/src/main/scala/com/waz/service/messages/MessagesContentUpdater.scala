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
package com.waz.service.messages

import com.waz.log.LogSE._
import com.waz.api.Message
import com.waz.api.Message.Status
import com.waz.content.GlobalPreferences.BackendDrift
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging.clock
import com.waz.service.otr.NotificationUiController
import com.waz.threading.Threading
import com.waz.utils._
import com.wire.signals.{Serialized, Signal}
import org.threeten.bp.Instant.now

import scala.collection.breakOut
import scala.concurrent.Future
import scala.concurrent.duration._

final class MessagesContentUpdater(selfId:          UserId,
                                   messagesStorage: MessagesStorage,
                                   convs:           ConversationStorage,
                                   deletions:       MsgDeletionStorage,
                                   buttonsStorage:  ButtonsStorage,
                                   prefs:           GlobalPreferences,
                                   userPrefs:       UserPreferences) extends DerivedLogTag {
  import Threading.Implicits.Background

  def getMessage(msgId: MessageId): Future[Option[MessageData]] = messagesStorage.getMessage(msgId)

  def deleteMessage(msg: MessageData): Future[Unit] = for {
    _ <- messagesStorage.delete(msg)
   _  <- if (msg.msgType == Message.Type.COMPOSITE) buttonsStorage.deleteAllForMessage(msg.id)
         else Future.successful(())
  } yield ()

  def deleteMessagesForConversation(convId: ConvId): Future[Unit] = for {
    msgIds <- messagesStorage.findMessageIds(convId)
    _      <- messagesStorage.deleteAll(convId)
    _      <- Future.sequence(msgIds.map(buttonsStorage.deleteAllForMessage))
  } yield ()

  def updateMessage(id: MessageId)(updater: MessageData => MessageData): Future[Option[MessageData]] =
    messagesStorage.update(id, updater) map {
      case Some((msg, updated)) if msg != updated =>
        assert(updated.id == id && updated.convId == msg.convId)
        Some(updated)
      case Some((msg, _)) =>
        Some(msg)
      case _ =>
        None
    }

  def updateButtonConfirmations(confirmations: Map[MessageId, Option[ButtonId]]): Future[Unit] =
    Future.sequence(confirmations.map {
      case (msgId, confirmedId) =>
        for {
          buttons <- buttonsStorage.findByMessage(msgId)
          _       <- buttonsStorage.updateAll2(buttons.map(_.id), {
                       case b if confirmedId.contains(b.buttonId) => b.copy(state = ButtonData.ButtonConfirmed)
                       case b                                     => b.copy(state = ButtonData.ButtonNotClicked)
                     })
        } yield ()
    }).map(_ => ())

  // removes messages and records deletion
  // this is used when user deletes a message manually (on local or remote device)
  def deleteOnUserRequest(ids: Seq[MessageId]) = for {
    _ <- deletions.insertAll(ids.map(id => MsgDeletion(id, now(clock))))
    _ <- messagesStorage.removeAll(ids)
    _ <- Future.sequence(ids.map(buttonsStorage.deleteAllForMessage))
  } yield ()

  def addButtons(buttons: Seq[ButtonData]): Future[Unit] = {
    val newButtons = buttons.map(b => b.id -> b).toMap
    buttonsStorage.updateOrCreateAll2(newButtons.keys, { (id, _) => newButtons(id) }).map(_ => ())
  }
  /**
    * @param exp Message Expiration precedence order:
   *            1. Team self-deleting messages feature config
   *            2. ConvExpiry: Conversation-specific timer setting
   *            3. One-time expiry (parameter exp),
   *            4. Which takes precedence over the MessageExpiry
   */
  def addLocalMessage(msg: MessageData, state: Status = Status.PENDING, exp: Option[Option[FiniteDuration]] = None, localTime: LocalInstant = LocalInstant.Now) =
    Serialized.future(s"add local message ${msg.convId}") {

      def expiration =
        if (MessageData.EphemeralMessageTypes(msg.msgType)) {
          def doesTeamEnablesExpiration = userPrefs(UserPreferences.AreSelfDeletingMessagesEnabled).signal
          val conversationExpiration = Signal.from(convs.get(msg.convId).map(_.fold(Option.empty[EphemeralDuration])(_.ephemeralExpiration)))
          def teamExpiration = userPrefs(UserPreferences.SelfDeletingMessagesEnforcedTimeout).signal
          Signal.zip(doesTeamEnablesExpiration, conversationExpiration, teamExpiration).map {
            case (false, _, _)                                    => None
            case (_, _, enforcedDuration) if enforcedDuration > 0 => Some(enforcedDuration.seconds)
            case (_, Some(ConvExpiry(d)), _)                      => Some(d)
            case (_, Some(MessageExpiry(d)), _)                   => exp.getOrElse(Some(d))
            case _ => exp.flatten
          }.future
        }
        else Future.successful(None)

      (for {
        time <- remoteTimeAfterLast(msg.convId) //TODO: can we find a way to save this only on the localTime of the message?
        exp  <- expiration
        m = returning(msg.copy(state = state, time = time, localTime = localTime, ephemeral = exp)) { m =>
          verbose(l"addLocalMessage: $m, exp: $exp")
        }
        res <- messagesStorage.addMessage(m)
      } yield res).recoverWith {
          case exception: Exception =>
            error(l"Error while adding local message: $exception")
            Future.failed(exception)
      }
    }

  def addLocalSentMessage(msg: MessageData, time: Option[RemoteInstant] = None) = Serialized.future(s"add local message ${msg.convId}") {
    verbose(l"addLocalSentMessage: $msg")
    time.fold(lastSentEventTime(msg.convId))(Future.successful).flatMap { t =>
      verbose(l"adding local sent message to storage, $t")
      messagesStorage.addMessage(msg.copy(state = Status.SENT, time = t + 1.millis, localTime = LocalInstant.Now))
    }
  }

  private def remoteTimeAfterLast(convId: ConvId) =
    messagesStorage.getLastMessage(convId).flatMap {
      case Some(msg) => Future successful msg.time
      case _ => convs.get(convId).map(_.fold(RemoteInstant.Epoch)(_.lastEventTime))
    }.flatMap { time =>
      prefs.preference(BackendDrift).apply().map(drift => (time + 1.millis) max LocalInstant.Now.toRemote(drift))
    }

  private def lastSentEventTime(convId: ConvId) =
    messagesStorage.getLastSentMessage(convId) flatMap {
      case Some(msg) => Future successful msg.time
      case _ => convs.get(convId).map(_.fold(RemoteInstant.Epoch)(_.lastEventTime))
    }

  /**
   * Updates last local message or creates new one.
   */
  def updateOrCreateLocalMessage(convId: ConvId, msgType: Message.Type, update: MessageData => MessageData, create: => MessageData) =
    Serialized.future(s"update-or-create-local-msg $convId $msgType") {
      lastSentEventTime(convId).flatMap { time =>
        messagesStorage.getLastSystemMessage(convId, msgType, time).flatMap {
          case Some(msg) if msg.isLocal => // got local message, try updating
            @volatile var shouldCreate = false
            verbose(l"got local message: $msg, will update")
            updateMessage(msg.id) { updatedMsg =>
              if (updatedMsg.isLocal) update(updatedMsg).copy(time = time)
              else { // msg was already synced, need to create new local message
                shouldCreate = true
                updatedMsg
              }
            } flatMap { res =>
              verbose(l"shouldCreate: $shouldCreate")
              if (shouldCreate) addLocalMessage(create).map(Some(_))
              else Future.successful(res)
            }
          case _ => addLocalMessage(create).map(Some(_))
        }
      }
    }

  private[service] def addMessages(convId: ConvId, msgs: Seq[MessageData]): Future[Set[MessageData]] =
    for {
      toAdd <- skipPreviouslyDeleted(msgs)
      (systemMsgs, contentMsgs) = toAdd.partition(_.isSystemMessage)
      sm <- addSystemMessages(convId, systemMsgs)
      cm <- addContentMessages(convId, contentMsgs)
    } yield sm.toSet ++ cm

  private def skipPreviouslyDeleted(msgs: Seq[MessageData]) =
    deletions.getAll(msgs.map(_.id)) map { deletions =>
      val ds: Set[MessageId] = deletions.collect { case Some(MsgDeletion(id, _)) => id } (breakOut)
      msgs.filter(m => !ds(m.id))
    }

  private def addSystemMessages(convId: ConvId, msgs: Seq[MessageData]): Future[Seq[MessageData]] =
    if (msgs.isEmpty) Future.successful(Seq.empty)
    else
      messagesStorage.getMessages(msgs.map(_.id): _*).flatMap { prev =>
        val prevIds: Set[MessageId] = prev.collect { case Some(m) => m.id } (breakOut)
        val toAdd = msgs.filterNot(m => prevIds.contains(m.id))

        RichFuture.traverseSequential(toAdd.groupBy(_.id).toSeq) { case (_, ms) =>
          val msg = ms.last
          messagesStorage.hasSystemMessage(convId, msg.time, msg.msgType, msg.userId).flatMap {
            case false =>
              lastSentEventTime(convId).flatMap { time =>
                // we don't have that exact system message but there still might be another message of the same type
                messagesStorage.getLastSystemMessage(convId, msg.msgType, time).flatMap {
                  case Some(m) if (m.msgType == Message.Type.MEMBER_JOIN || m.msgType == Message.Type.MEMBER_LEAVE || m.msgType == Message.Type.MEMBER_LEAVE_DUE_TO_LEGAL_HOLD) && m.members == msg.members && !m.isLocal =>
                    // we have a duplicate, do nothing
                    Future.successful(None)
                  case Some(m) if (m.msgType == Message.Type.MEMBER_JOIN || m.msgType == Message.Type.MEMBER_LEAVE || m.msgType == Message.Type.MEMBER_LEAVE_DUE_TO_LEGAL_HOLD) && m.isLocal =>
                    updateMessage(m.id)(_.copy(members = m.members ++ msg.members, localTime = m.localTime))
                  case Some(m) if m.isLocal =>
                    messagesStorage.remove(m.id).flatMap(_ => messagesStorage.addMessage(msg.copy(localTime = m.localTime))).map(Some(_))
                  case _ =>
                    messagesStorage.addMessage(msg).map(Some(_))
                }
              }
            case true =>
              Future.successful(None)
          }
        }.map(_.flatten)
      }

  private def addContentMessages(convId: ConvId, msgs: Seq[MessageData]): Future[Set[MessageData]] =
    msgs.size match {
      case 0 =>
        Future.successful(Set.empty)
      case 1 =>
        val updater: MessageData => MessageData = msg => Merger.merge(Seq(msg) ++ msgs)
        messagesStorage.updateOrCreate(msgs.head.id, updater, creator = msgs.head).map(Set(_))
      case _ =>
        val updaters = msgs.groupBy(_.id).map { case (id, data) => id -> Merger.messageDataMerger(data) }
        messagesStorage.updateOrCreateAll(updaters)
    }

  private[service] def addMessage(msg: MessageData): Future[Option[MessageData]] = addMessages(msg.convId, Seq(msg)).map(_.headOption)

  // updates server timestamp for local messages, this should make sure that local messages are ordered correctly after one of them is sent
  def updateLocalMessageTimes(conv: ConvId, prevTime: RemoteInstant, time: RemoteInstant) =
    messagesStorage.findLocalFrom(conv, prevTime) flatMap { local =>
      messagesStorage updateAll2(local.map(_.id), { m =>
        if (m.isLocal) m.copy(time = time + (m.time.toEpochMilli - prevTime.toEpochMilli).millis) else m
      })
    }

  private object Merger {

    /**
      * A merging function for a given sequence of messages. It is assumed that the messages
      * share the same id.
      */
    val messageDataMerger: Seq[MessageData] => Option[MessageData] => MessageData =
      data => { prev => merge(prev.toSeq ++ data) }

    /**
      * Merges data from multiple events into a single message.
      * @param msgs
      * @return
      */
    def merge(msgs: Seq[MessageData]): MessageData = {
      if (msgs.size == 1)
        msgs.head
      else {
        msgs.reduce { (prev, msg) =>
          if (prev.isLocal && prev.userId == msg.userId)
            mergeLocal(localMessage = prev, msg)
          else if (prev.userId == msg.userId)
            mergeMatching(prev, msg)
          else {
            warn(l"got message id conflict, will add it with random id, existing: $prev, new: $msg")
            addMessage(msg.copy(id = MessageId()))
            prev
          }
        }
      }
    }

    private def mergeLocal(localMessage: MessageData, msg: MessageData): MessageData =
      msg.copy(id = localMessage.id, localTime = localMessage.localTime)

    private def mergeMatching(prev: MessageData, msg: MessageData): MessageData = {
      // `AssetId` (for uploaded assets) takes highest priority.
      val assetId = List(msg.assetId, prev.assetId)
        .collectFirst { case Some(id: AssetId) => id }
        .orElse(msg.assetId)
        .orElse(prev.assetId)

      val u = prev.copy(
        msgType       = if (msg.msgType != Message.Type.UNKNOWN) msg.msgType else prev.msgType ,
        time          = if (msg.time.isBefore(prev.time) || prev.isLocal) msg.time else prev.time,
        genericMsgs        = prev.genericMsgs ++ msg.genericMsgs,
        content       = msg.content,
        quote         = msg.quote,
        assetId       = assetId
      )

      prev.msgType match {
        case Message.Type.RECALLED => prev // Ignore updates to already recalled message.
        case _ => u
      }
    }
  }
}
