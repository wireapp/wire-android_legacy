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
package com.waz.zclient.messages

import android.content.Context
import androidx.paging.PositionalDataSource
import com.waz.content.MessageAndLikesStorage
import com.waz.db.{CursorIterator, Reader}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.MessageData.MessageDataDao
import com.waz.model.{MessageData, MessageId, RemoteInstant}
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading.Implicits.Background
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.wrappers.DBCursor
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageDataSource.{MessageEntry, MessageEntryReader}
import com.waz.zclient.{Injectable, Injector}

import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class MessageDataSource(val cursor: Option[DBCursor])(implicit inj: Injector, ec: EventContext, cxt: Context)
  extends PositionalDataSource[MessageAndLikes]
    with Injectable
    with DerivedLogTag {

  private val messageAndLikesStorage = inject[Signal[MessageAndLikesStorage]]

  private def load(start: Int, count: Int): Future[Seq[MessageAndLikes]] = cursor match {
    case Some(c) if !c.isClosed =>
      var msgData: Seq[MessageData] = Nil
      synchronized {
        val totalCount = c.getCount
        msgData = (start until (start + count)).flatMap { pos =>
          if (pos < totalCount && c.moveToPosition(pos)) {
            List(MessageEntry(c))
          }
          else
            Nil
        }
      }
      messageAndLikesStorage.head.flatMap(_.combineWithLikes(msgData))
    case _ => Future.successful(Nil)
  }

  override def loadInitial(params: PositionalDataSource.LoadInitialParams, callback: PositionalDataSource.LoadInitialCallback[MessageAndLikes]): Unit = {
    val total = totalCount
    val start = PositionalDataSource.computeInitialLoadPosition(params, total)
    val size = PositionalDataSource.computeInitialLoadSize(params, start, total)

    try {
      val data = Await.result(load(start, size), 5.seconds)
      callback.onResult(data.asJava, start, total)
    } catch {
      case _: Throwable =>
        load(start, size).foreach(data => callback.onResult(data.asJava, start, total))
    }
  }

  override def loadRange(params: PositionalDataSource.LoadRangeParams, callback: PositionalDataSource.LoadRangeCallback[MessageAndLikes]): Unit = {
    load(params.startPosition, params.loadSize).onComplete {
      case Success(data) =>
        callback.onResult(data.asJava)
      case Failure(e) =>
        error(l"loadRange error:", e)
    }
  }

  def positionForMessage(messageId: MessageId): Option[Int] = synchronized {
    cursor.filter(!_.isClosed).map { c =>
      new CursorIterator(c)(MessageEntryReader).indexWhere(e => e.id == messageId)
    }
  }

  def positionForMessage(time: RemoteInstant): Option[Int] = synchronized {
    cursor.filter(!_.isClosed).map { c =>
      new CursorIterator(c)(MessageEntryReader).indexWhere(e => e.time == time)
    }
  }

  def totalCount: Int = cursor.map(_.getCount).getOrElse(0)

  override def invalidate(): Unit = {
    super.invalidate()
    synchronized {
      cursor.foreach(_.close())
    }
  }
}

object MessageDataSource {
  object MessageEntry {
    def apply(cursor: DBCursor): MessageData = MessageDataDao(cursor)
  }
  implicit object MessageEntryReader extends Reader[MessageData] {
    override def apply(implicit c: DBCursor): MessageData = MessageEntry(c)
  }
}
