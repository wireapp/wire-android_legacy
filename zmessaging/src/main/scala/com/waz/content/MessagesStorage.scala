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
package com.waz.content

import java.util.concurrent.ConcurrentHashMap

import android.content.Context
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.impl.ErrorResponse
import com.waz.api.{Message, MessageFilter}
import com.waz.model.ConversationData.UnreadCount
import com.waz.model.MessageData.{MessageDataDao, MessageEntry}
import com.waz.model._
import com.waz.service.Timeouts
import com.waz.service.messages.MessageAndLikes
import com.waz.service.tracking.TrackingService
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils._
import com.waz.utils.events.{EventStream, Signal, SourceStream}

import scala.collection._
import scala.concurrent.Future
import scala.util.Failure

trait MessagesStorage extends CachedStorage[MessageId, MessageData] {

  //def for tests
  def messageAdded:    EventStream[Seq[MessageData]]
  def messageUpdated:  EventStream[Seq[(MessageData, MessageData)]]
  def messageChanged:  EventStream[Seq[MessageData]]
  def onMessageSent:   SourceStream[MessageData]
  def onMessageFailed: SourceStream[(MessageData, ErrorResponse)]

  def delete(msg: MessageData): Future[Unit]
  def deleteAll(conv: ConvId):  Future[Unit]

  def addMessage(msg: MessageData): Future[MessageData]

  def getMessage(id: MessageId):    Future[Option[MessageData]]
  def getMessages(ids: MessageId*): Future[Seq[Option[MessageData]]]

  def msgsIndex(conv: ConvId): Future[ConvMessagesIndex]
  def msgsFilteredIndex(conv: ConvId, messageFilter: MessageFilter): Future[ConvMessagesIndex]

  def findLocalFrom(conv: ConvId, time: RemoteInstant): Future[IndexedSeq[MessageData]]

  //System message events no longer have IDs, so we need to search by type, timestamp and sender
  def hasSystemMessage(conv: ConvId, serverTime: RemoteInstant, tpe: Message.Type, sender: UserId): Future[Boolean]

  def getLastMessage(conv: ConvId): Future[Option[MessageData]]
  def getLastSentMessage(conv: ConvId): Future[Option[MessageData]]
  def lastLocalMessage(conv: ConvId, tpe: Message.Type): Future[Option[MessageData]]
  def countLaterThan(conv: ConvId, time: RemoteInstant): Future[Long]

  def findMessagesFrom(conv: ConvId, time: RemoteInstant): Future[IndexedSeq[MessageData]]

  def clear(convId: ConvId, clearTime: RemoteInstant): Future[Unit]

  def lastMessageFromSelfAndFromOther(conv: ConvId): Signal[(Option[MessageData], Option[MessageData])]

  def isQuoteOfSelf(message: MessageData): Future[Boolean]
  def countUnread(conv: ConvId, lastReadTime: RemoteInstant): Future[UnreadCount]
}

class MessagesStorageImpl(context: Context,
                          storage: ZmsDatabase,
                          userId: UserId,
                          convs: ConversationStorage,
                          users: UsersStorage,
                          msgAndLikes: => MessageAndLikesStorage,
                          timeouts: Timeouts,
                          tracking: TrackingService) extends
    CachedStorageImpl[MessageId, MessageData](new TrimmingLruCache[MessageId, Option[MessageData]](context, Fixed(MessagesStorage.cacheSize)), storage)(MessageDataDao, "MessagesStorage_Cached") with MessagesStorage {

  import com.waz.utils.events.EventContext.Implicits.global

  private implicit val dispatcher = new SerialDispatchQueue(name = "MessagesStorage")

  override val messageAdded = onAdded
  override val messageUpdated = onUpdated

  val messageChanged = EventStream.union(messageAdded, messageUpdated.map(_.map(_._2)))

  //For tracking on UI
  val onMessageSent = EventStream[MessageData]()
  val onMessageFailed = EventStream[(MessageData, ErrorResponse)]()

  private val indexes = new ConcurrentHashMap[ConvId, ConvMessagesIndex]
  private val filteredIndexes = new MultiKeyLruCache[ConvId, MessageFilter, ConvMessagesIndex](MessagesStorage.filteredMessagesCacheSize)

  def msgsIndex(conv: ConvId): Future[ConvMessagesIndex] =
    Option(indexes.get(conv)).fold {
      Future(returning(new ConvMessagesIndex(conv, this, userId, users, convs, msgAndLikes, storage, tracking))(indexes.put(conv, _)))
    } {
      Future.successful
    }

  def msgsFilteredIndex(conv: ConvId, messageFilter: MessageFilter): Future[ConvMessagesIndex] =
    filteredIndexes.get(conv, messageFilter).fold {
      Future(returning(new ConvMessagesIndex(conv, this, userId, users, convs, msgAndLikes, storage, tracking, filter = Some(messageFilter)))(filteredIndexes.put(conv, messageFilter, _)))
    } {
      Future.successful
    }

  def msgsFilteredIndex(conv: ConvId): Seq[ConvMessagesIndex] = filteredIndexes.get(conv).values.toSeq

  onAdded { added =>
    Future.traverse(added.groupBy(_.convId)) { case (convId, msgs) =>
      msgsFilteredIndex(convId).foreach(_.add(msgs))
      msgsIndex(convId).flatMap { index =>
        index.add(msgs).flatMap(_ => index.firstMessageId) map { first =>
          // XXX: calling update here is a bit ugly
          val ms = msgs.map {
            case msg if first.contains(msg.id) =>
              update(msg.id, _.copy(firstMessage = first.contains(msg.id)))
              msg.copy(firstMessage = first.contains(msg.id))
            case msg => msg
          }
        }
      }
    } .recoverWithLog()
  }

  onUpdated { updates =>
    Future.traverse(updates.groupBy(_._1.convId)) { case (convId, msgs) =>{
        msgsFilteredIndex(convId).foreach(_.update(msgs))
        for {
          index <- msgsIndex(convId)
          _ <- index.update(msgs)
        } yield ()
      } .recoverWithLog()
    }
  }

  convs.onUpdated.on(dispatcher) { _.foreach {
    case (prev, updated) if updated.lastRead != prev.lastRead =>
      verbose(s"lastRead of conversation ${updated.id} updated to ${updated.lastRead}, will update unread count")
      msgsIndex(updated.id).map(_.updateLastRead(updated)).recoverWithLog()
    case _ => // ignore
  } }

  override def addMessage(msg: MessageData) = put(msg.id, msg)

  override def countUnread(conv: ConvId, lastReadTime: RemoteInstant): Future[UnreadCount] = {
    // if a message is both a mention and a quote, we count it as a mention
    def isQuoteButNotMention(m: MessageData) =
      if (m.hasMentionOf(userId)) Future.successful(false)
      else isQuoteOfSelf(m)

    storage {
      MessageDataDao.findMessagesFrom(conv, lastReadTime)(_)
    }.future.flatMap { msgs =>
      msgs.acquire { msgs =>
        val unread = msgs.filter { m => !m.isLocal && m.convId == conv && m.time.isAfter(lastReadTime) && !m.isDeleted && m.userId != userId && m.msgType != Message.Type.UNKNOWN }.toVector
        Future.sequence(unread.map(isQuoteButNotMention)).map(_.count(q => q)).map(unreadQuotes =>
          UnreadCount(
            unread.count(m => !m.isSystemMessage && m.msgType != Message.Type.KNOCK && !m.hasMentionOf(userId)) - unreadQuotes,
            unread.count(_.msgType == Message.Type.MISSED_CALL),
            unread.count(_.msgType == Message.Type.KNOCK),
            unread.count(_.hasMentionOf(userId)),
            unreadQuotes
          )
        )
      }
    }

  }

  override def isQuoteOfSelf(msg: MessageData) =
    if (!msg.quoteValidity) Future.successful(false)
    else msg.quote.fold(Future.successful(false))(quoteId => get(quoteId).map(_.exists(_.userId == userId)))

  def countSentByType(selfUserId: UserId, tpe: Message.Type): Future[Int] = storage(MessageDataDao.countSentByType(selfUserId, tpe)(_).toInt)

  def countMessages(conv: ConvId, p: MessageEntry => Boolean): Future[Int] = storage(MessageDataDao.countMessages(conv, p)(_))

  def countLaterThan(conv: ConvId, time: RemoteInstant): Future[Long] = storage(MessageDataDao.countLaterThan(conv, time)(_))

  override def getMessage(id: MessageId) = get(id)

  override def getMessages(ids: MessageId*) = getAll(ids)

  def getEntries(conv: ConvId) = Signal.future(msgsIndex(conv)).flatMap(_.signals.messagesCursor)

  def lastMessage(conv: ConvId) = Signal.future(msgsIndex(conv)).flatMap(_.signals.lastMessage)

  def lastMessageFromSelfAndFromOther(conv: ConvId) = Signal.future(msgsIndex(conv)).flatMap(mi => mi.signals.lastMessageFromSelf zip mi.signals.lastMessageFromOther)

  def getLastMessage(conv: ConvId) = msgsIndex(conv).flatMap(_.getLastMessage)

  def getLastSentMessage(conv: ConvId) = msgsIndex(conv).flatMap(_.getLastSentMessage)

  def unreadCount(conv: ConvId): Signal[Int] = Signal.future(msgsIndex(conv)).flatMap(_.signals.unreadCount).map(_.messages)

  def lastRead(conv: ConvId) = Signal.future(msgsIndex(conv)).flatMap(_.signals.lastReadTime)

  override def lastLocalMessage(conv: ConvId, tpe: Message.Type) =
    msgsIndex(conv).flatMap(_.lastLocalMessage(tpe)).flatMap {
      case Some(id) => get(id)
      case _ => CancellableFuture.successful(None)
    }

  //TODO: use local instant?
  override def findLocalFrom(conv: ConvId, time: RemoteInstant) =
    find(m => m.convId == conv && m.isLocal && !m.time.isBefore(time), MessageDataDao.findLocalFrom(conv, time)(_), identity)

  def findMessagesFrom(conv: ConvId, time: RemoteInstant) =
    find(m => m.convId == conv && !m.time.isBefore(time), MessageDataDao.findMessagesFrom(conv, time)(_), identity)

  override def delete(msg: MessageData) =
    for {
      _ <- super.remove(msg.id)
      _ <- Future(msgsFilteredIndex(msg.convId).foreach(_.delete(msg)))
      index <- msgsIndex(msg.convId)
      _ <- index.delete(msg)
      _ <- storage.flushWALToDatabase()
    } yield ()

  override def remove(id: MessageId): Future[Unit] =
    getMessage(id) flatMap {
      case Some(msg) => delete(msg)
      case None =>
        warn(s"No message found for: $id")
        Future.successful(())
    }

  override def removeAll(keys: Iterable[MessageId]): Future[Unit] =
    for {
      fromDb <- getAll(keys)
      msgs = fromDb.collect { case Some(m) => m }
      _ <- super.removeAll(keys)
      _ <- Future.traverse(msgs) { msg =>
        Future(msgsFilteredIndex(msg.convId).foreach(_.delete(msg))).zip(
        msgsIndex(msg.convId).flatMap(_.delete(msg)))
      }
      _ <- storage.flushWALToDatabase()
    } yield ()

  def clear(conv: ConvId, upTo: RemoteInstant): Future[Unit] = {
    verbose(s"clear($conv, $upTo)")
    for {
      _ <- storage { MessageDataDao.deleteUpTo(conv, upTo)(_) } .future
      _ <- storage { MessageContentIndexDao.deleteUpTo(conv, upTo)(_) } .future
      _ <- deleteCached(m => m.convId == conv && ! m.time.isAfter(upTo))
      _ <- Future(msgsFilteredIndex(conv).foreach(_.delete(upTo)))
      _ <- msgsIndex(conv).flatMap(_.delete(upTo))
      _ <- storage.flushWALToDatabase()
    } yield ()
  }

  override def deleteAll(conv: ConvId) = {
    verbose(s"deleteAll($conv)")
    for {
      _ <- storage { MessageDataDao.deleteForConv(conv)(_) } .future
      _ <- storage { MessageContentIndexDao.deleteForConv(conv)(_) } .future
      _ <- deleteCached(_.convId == conv)
      _ <- Future(msgsFilteredIndex(conv).foreach(_.delete()))
      _ <- msgsIndex(conv).flatMap(_.delete())
      _ <- storage.flushWALToDatabase()
    } yield ()
  }

  override def hasSystemMessage(conv: ConvId, serverTime: RemoteInstant, tpe: Message.Type, sender: UserId) = {
    def matches(msg: MessageData) = msg.convId == conv && msg.time == serverTime && msg.msgType == tpe && msg.userId == sender
    find(matches, MessageDataDao.findSystemMessage(conv, serverTime, tpe, sender)(_), identity).map(_.size).map {
      case 0 => false
      case 1 => true
      case _ =>
        warn("Found multiple system messages with given timestamp")
        true
    }
  }
}

object MessagesStorage {
  val cacheSize = 12048
  val filteredMessagesCacheSize = 32
  val FirstMessageTypes = {
    import Message.Type._
    Set(TEXT, TEXT_EMOJI_ONLY, KNOCK, ASSET, ANY_ASSET, VIDEO_ASSET, AUDIO_ASSET, LOCATION)
  }
}

trait MessageAndLikesStorage {
  val onUpdate: EventStream[MessageId]
  def apply(ids: Seq[MessageId]): Future[Seq[MessageAndLikes]]
  def getMessageAndLikes(id: MessageId): Future[Option[MessageAndLikes]]
  def combineWithLikes(msg: MessageData): Future[MessageAndLikes]
  def combine(msg: MessageData, likes: Likes, selfUserId: UserId, quote: Option[MessageData]): MessageAndLikes
  def sortedLikes(likes: Likes, selfUserId: UserId): (IndexedSeq[UserId], Boolean)
}

class MessageAndLikesStorageImpl(selfUserId: UserId, messages: MessagesStorage, likings: ReactionsStorage) extends MessageAndLikesStorage {
  import com.waz.threading.Threading.Implicits.Background
  import com.waz.utils.events.EventContext.Implicits.global

  val onUpdate = EventStream[MessageId]() // TODO: use batching, maybe report new message data instead of just id

  messages.onDeleted { ids => ids foreach { onUpdate ! _ } }
  messages.messageChanged { ms => ms foreach { m => onUpdate ! m.id }}
  likings.onChanged { _ foreach { l => onUpdate ! l.message } }


  def apply(ids: Seq[MessageId]): Future[Seq[MessageAndLikes]] = for {
    msgs <- messages.getMessages(ids: _*).map(_.flatten)
    likes <- getLikes(msgs)
    quotes <- getQuotes(msgs)
  } yield msgs.map { msg =>
    combine(msg, likes.getOrElse(msg.id, Likes.Empty(msg.id)), selfUserId, quotes.get(msg.id).flatten)
  }

  def getMessageAndLikes(id: MessageId): Future[Option[MessageAndLikes]] = apply(Seq(id)).map(_.headOption)

  override def combineWithLikes(msg: MessageData): Future[MessageAndLikes] = for {
    likes <- getLikes(Seq(msg))
    quotes <- getQuotes(Seq(msg))
  } yield combine(msg, likes.getOrElse(msg.id, Likes.Empty(msg.id)), selfUserId, quotes.get(msg.id).flatten)

  def getQuotes(msgs: Seq[MessageData]): Future[Map[MessageId, Option[MessageData]]] = {
    Future.sequence(msgs.flatMap(m => m.quote.map(m.id -> _).toSeq).map {
      case (m, q) => messages.getMessage(q).map(m -> _)
    }).map(_.toMap)
  }

  def getLikes(msgs: Seq[MessageData]): Future[Map[MessageId, Likes]] = {
    likings.loadAll(msgs.map(_.id)).map { likes =>
      likes.by[MessageId, Map](_.message)
    }
  }

  def combine(msg: MessageData, likes: Likes, selfUserId: UserId, quote: Option[MessageData]): MessageAndLikes =
    if (likes.likers.isEmpty) MessageAndLikes(msg, Vector(), likedBySelf = false, quote)
    else sortedLikes(likes, selfUserId) match { case (likers, selfLikes) => MessageAndLikes(msg, likers, selfLikes, quote) }


  def sortedLikes(likes: Likes, selfUserId: UserId): (IndexedSeq[UserId], Boolean) =
    (likes.likers.toVector.sortBy(_._2).map(_._1), likes.likers contains selfUserId)
}
