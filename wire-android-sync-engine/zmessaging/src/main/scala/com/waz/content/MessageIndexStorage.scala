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

import java.util.concurrent.TimeUnit

import android.content.Context
import com.waz.api.ContentSearchQuery
import com.waz.db.{CursorIterator, Reader}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.tracking.TrackingService
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.wrappers.DBCursor
import com.waz.utils.{CachedStorageImpl, TrimmingLruCache}
import com.waz.utils._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class MessageIndexStorage(context: Context, storage: ZmsDatabase, messagesStorage: MessagesStorage, loader: MessageAndLikesStorage, tracking: TrackingService, conversationStorage: ConversationStorage)
  extends CachedStorageImpl[MessageId, MessageContentIndexEntry](new TrimmingLruCache(context, Fixed(MessageContentIndex.MaxSearchResults)), storage)(MessageContentIndexDao, LogTag("MessageIndexStorage_Cached"))
    with DerivedLogTag {

  import MessageIndexStorage._
  import MessageContentIndex.TextMessageTypes
  import com.waz.utils.events.EventContext.Implicits.global

  private implicit val dispatcher = new SerialDispatchQueue(name = "MessageIndexStorage")

  private def entry(m: MessageData) =
    MessageContentIndexEntry(m.id, m.convId, ContentSearchQuery.transliterated(m.contentString), m.time)

  messagesStorage.onAdded { added =>
    insertAll(added.filter(m => TextMessageTypes.contains(m.msgType) && !m.isEphemeral).map(entry))
  }

  messagesStorage.onUpdated { updated =>
    val entries = updated.collect { case (_, m) if TextMessageTypes.contains(m.msgType) && !m.isEphemeral => entry(m) }
    //FTS tables ignore UNIQUE or PKEY constraints so we have to force the replace
    removeAll(entries.map(_.messageId))
    insertAll(entries)
  }

  messagesStorage.onDeleted { removed =>
    if (removed.nonEmpty) removeAll(removed)
  }

  def searchText(contentSearchQuery: ContentSearchQuery, convId: Option[ConvId]): Future[MessagesCursor] =
    for {
      cursor <- storage.read(MessageContentIndexDao.findContent(contentSearchQuery, convId)(_)) // find messages
      _ <- getAll(CursorIterator.list[MessageId](cursor, close = false)(MsgIdReader)) // prefetch index entries to ensure following get calls are fast
      lastRead <- convId.fold2(Future.successful(None), conversationStorage.get(_).map(_.map(_.lastRead)))
    } yield
      new MessagesCursor(cursor, 0, lastRead.getOrElse(RemoteInstant.Epoch), loader, tracking)(MessagesCursor.Descending)

  def matchingMessages(contentSearchQuery: ContentSearchQuery, convId: Option[ConvId]): Future[Set[MessageId]] =
    storage.read { implicit db =>
      CursorIterator.list[MessageId](MessageContentIndexDao.findContent(contentSearchQuery, convId))(MsgIdReader).toSet
    }

  def getNormalizedContentForMessage(messageId: MessageId): Future[Option[String]] =
    get(messageId).map(_.map(_.content))
}

object MessageIndexStorage {
  val UpdateOldMessagesThrottle = FiniteDuration(1, TimeUnit.SECONDS)

  implicit object MsgIdReader extends Reader[MessageId] {
    override def apply(implicit c: DBCursor): MessageId = MessageId(c.getString(0))
  }
}
