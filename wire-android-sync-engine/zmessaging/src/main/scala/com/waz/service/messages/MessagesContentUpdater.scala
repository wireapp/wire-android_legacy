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
import com.waz.threading.Threading
import com.waz.utils._
import org.threeten.bp.Instant.now

import scala.collection.breakOut
import scala.concurrent.Future
import scala.concurrent.duration._

class MessagesContentUpdater(messagesStorage: MessagesStorage,
                             convs:           ConversationStorage,
                             deletions:       MsgDeletionStorage,
                             prefs:           GlobalPreferences) extends DerivedLogTag {

  import Threading.Implicits.Background

  def getMessage(msgId: MessageId) = messagesStorage.getMessage(msgId)

  def deleteMessage(msg: MessageData) = messagesStorage.delete(msg)

  def deleteMessagesForConversation(convId: ConvId): Future[Unit] = messagesStorage.deleteAll(convId)

  def updateMessage(id: MessageId)(updater: MessageData => MessageData): Future[Option[MessageData]] = messagesStorage.update(id, updater) map {
    case Some((msg, updated)) if msg != updated =>
      assert(updated.id == id && updated.convId == msg.convId)
      Some(updated)
    case _ =>
      None
  }

  // removes messages and records deletion
  // this is used when user deletes a message manually (on local or remote device)
  def deleteOnUserRequest(ids: Seq[MessageId]) =
    deletions.insertAll(ids.map(id => MsgDeletion(id, now(clock)))) flatMap { _ =>
      messagesStorage.removeAll(ids)
    }

  /**
    * @param exp ConvExpiry takes precedence over one-time expiry (exp), which takes precedence over the MessageExpiry
    */
  def addLocalMessage(msg: MessageData, state: Status = Status.PENDING, exp: Option[Option[FiniteDuration]] = None, localTime: LocalInstant = LocalInstant.Now) =
    Serialized.future("add local message", msg.convId) {

      def expiration =
        if (MessageData.EphemeralMessageTypes(msg.msgType))
          convs.get(msg.convId).map(_.fold(Option.empty[EphemeralDuration])(_.ephemeralExpiration)).map {
            case Some(ConvExpiry(d))    => Some(d)
            case Some(MessageExpiry(d)) => exp.getOrElse(Some(d))
            case _                      => exp.flatten
          }
        else Future.successful(None)

      for {
        time <- remoteTimeAfterLast(msg.convId) //TODO: can we find a way to save this only on the localTime of the message?
        exp  <- expiration
        m = returning(msg.copy(state = state, time = time, localTime = localTime, ephemeral = exp)) { m =>
          verbose(l"addLocalMessage: $m, exp: $exp")
        }
        res <- messagesStorage.addMessage(m)
      } yield res
    }

  def addLocalSentMessage(msg: MessageData, time: Option[RemoteInstant] = None) = Serialized.future("add local message", msg.convId) {
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
    Serialized.future("update-or-create-local-msg", convId, msgType) {
      messagesStorage.lastLocalMessage(convId, msgType) flatMap {
        case Some(msg) => // got local message, try updating
          @volatile var shouldCreate = false
          verbose(l"got local message: $msg, will update")
          updateMessage(msg.id) { msg =>
            if (msg.isLocal) update(msg)
            else { // msg was already synced, need to create new local message
              shouldCreate = true
              msg
            }
          } flatMap { res =>
            verbose(l"shouldCreate: $shouldCreate")
            if (shouldCreate) addLocalMessage(create).map(Some(_))
            else Future.successful(res)
          }
        case _ => addLocalMessage(create).map(Some(_))
      }
    }

  private[service] def addMessages(convId: ConvId, msgs: Seq[MessageData]): Future[Set[MessageData]] =
    for {
      toAdd <- skipPreviouslyDeleted(msgs)
      (systemMsgs, contentMsgs) = toAdd.partition(_.isSystemMessage)
      sm <- addSystemMessages(convId, systemMsgs)
      _  =  verbose(l"SYNC system messages added (${sm.size})")
      cm <- addContentMessages(convId, contentMsgs)
      _  =  verbose(l"SYNC content messages added (${cm.size})")
    } yield sm.toSet ++ cm

  private def skipPreviouslyDeleted(msgs: Seq[MessageData]) =
    deletions.getAll(msgs.map(_.id)) map { deletions =>
      val ds: Set[MessageId] = deletions.collect { case Some(MsgDeletion(id, _)) => id } (breakOut)
      msgs.filter(m => !ds(m.id))
    }

  private def addSystemMessages(convId: ConvId, msgs: Seq[MessageData]): Future[Seq[MessageData]] =
    if (msgs.isEmpty) Future.successful(Seq.empty)
    else {
      messagesStorage.getMessages(msgs.map(_.id): _*) flatMap { prev =>
        val prevIds: Set[MessageId] = prev.collect { case Some(m) => m.id } (breakOut)
        val toAdd = msgs.filterNot(m => prevIds.contains(m.id))

        RichFuture.traverseSequential(toAdd.groupBy(_.id).toSeq) { case (_, ms) =>
          val msg = ms.last
          messagesStorage.hasSystemMessage(convId, msg.time, msg.msgType, msg.userId).flatMap {
            case false =>
              messagesStorage.lastLocalMessage(convId, msg.msgType).flatMap {
                case Some(m) if m.userId == msg.userId =>
                  verbose(l"lastLocalMessage(${msg.msgType}) : $m")

                  if (m.msgType == Message.Type.MEMBER_JOIN || m.msgType == Message.Type.MEMBER_LEAVE) {
                    val remaining = m.members.diff(msg.members)
                    if (remaining.nonEmpty) addMessage(m.copy(id = MessageId(), members = remaining))
                  }
                  messagesStorage.remove(m.id).flatMap(_ => messagesStorage.addMessage(msg.copy(localTime = m.localTime)))
                case res =>
                  verbose(l"lastLocalMessage(${msg.msgType}) returned: $res")
                  messagesStorage.addMessage(msg)
              }.map(Some(_))
            case true =>
              Future.successful(None)
          }
        }.map(_.flatten)
      }
    }

  private def addContentMessages(convId: ConvId, msgs: Seq[MessageData]): Future[Set[MessageData]] = {
    verbose(l"SYNC addContentMessages($convId, ${msgs.map(_.id)})")

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
  }

  private[service] def addMessage(msg: MessageData): Future[Option[MessageData]] = addMessages(msg.convId, Seq(msg)).map(_.headOption)

  // updates server timestamp for local messages, this should make sure that local messages are ordered correctly after one of them is sent
  def updateLocalMessageTimes(conv: ConvId, prevTime: RemoteInstant, time: RemoteInstant) =
    messagesStorage.findLocalFrom(conv, prevTime) flatMap { local =>
      verbose(l"local messages from $prevTime: $local")
      messagesStorage updateAll2(local.map(_.id), { m =>
        verbose(l"try updating local message time, msg: $m, time: $time")
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
          verbose(l"msgs reduce from $prev to $msg")

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
        protos        = prev.protos ++ msg.protos,
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
