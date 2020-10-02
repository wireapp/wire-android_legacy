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

import androidx.collection.LruCache
import com.waz.content.MessagesCursor.Entry
import com.waz.db.{CursorIterator, Reader, ReverseCursorIterator}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.service.messages.MessageAndLikes
import com.wire.signals.{DispatchQueue, EventStream, SerialDispatchQueue}
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.wrappers.DBCursor

import scala.collection.Searching.{Found, InsertionPoint}
import scala.concurrent.{Await, Future}
import scala.util.{Success, Try}

trait MsgCursor {
  def size: Int
  def lastReadIndex: Int
  def lastReadTime: RemoteInstant
  def apply(index: Int): MessageAndLikes
  def indexOf(time: RemoteInstant): Int
  def close(): Unit
}

class MessagesCursor(cursor: DBCursor,
                     override val lastReadIndex: Int,
                     val lastReadTime: RemoteInstant,
                     loader: MessageAndLikesStorage)(implicit ordering: Ordering[RemoteInstant]) extends MsgCursor with DerivedLogTag { self =>
  import MessagesCursor._
  import com.wire.signals.EventContext.Implicits.global

  import scala.concurrent.duration._

  private implicit val dispatcher = SerialDispatchQueue(name = "MessagesCursor")

  private val messages = new LruCache[MessageId, MessageAndLikes](WindowSize * 2)
  private val quotes = new LruCache[MessageId, Seq[MessageId]](WindowSize * 2)
  private val windowLoader = new WindowLoader(cursor)

  val createTime = LocalInstant.Now //used in UI

  override def size = cursor.getCount

  val onUpdate = EventStream[(MessageAndLikes, MessageAndLikes)]()

  private val subs = Seq (
    loader.onUpdate { id =>

      val replies = Option(quotes.get(id)).map(qs => qs.map(q => Option(messages.get(q)))).getOrElse(Seq())

      (replies :+ Option(messages.get(id))).flatten.foreach { prev =>

        loader.getMessageAndLikes(prev.message.id).foreach(_ foreach { mAndL =>
          putMessage(mAndL)
          onUpdate ! (prev, mAndL)
        })
      }
    }
  )

  verbose(l"init(_, $lastReadIndex, $lastReadTime) - lastRead: $lastReadIndex")

  override def close(): Unit = {
    Threading.assertUiThread()
    subs foreach(_.destroy())
    Future(if (! cursor.isClosed) cursor.close())
  }

  override def finalize(): Unit = Future(if (! cursor.isClosed) cursor.close())

  def prefetchById(id: MessageId): Future[Unit] = {
    verbose(l"prefetchById($id)")
    for {
      m <- loader(Seq(id))
      i <- m.headOption.fold(Future successful lastReadIndex) { d => asyncIndexOf(d.message.time) }
      _ <- prefetch(if (i < 0) lastReadIndex else i)
    } yield ()
  }

  /** will block if message is outside of prefetched window */
  override def indexOf(time: RemoteInstant): Int =
    returning(Try(Await.result(asyncIndexOf(time), 10.seconds)).getOrElse(lastReadIndex)) { index =>
      verbose(l"indexOf($time) = $index, lastReadTime: $lastReadTime")
    }

  def asyncIndexOf(time: RemoteInstant, binarySearch: Boolean = false): Future[Int] = {
    def cursorSearch(from: Int, to: Int) = Future {
      logTime(l"time: $time not found in pre-fetched window, had to go through cursor, binarySearch: $binarySearch") {
        val index = if (binarySearch) cursorBinarySearch(time, from, to) else cursorLinearSearch(time, from, to)
        verbose(l"index in cursor: $index")
        index
      }
    }

    def cursorSearchBounds(window: IndexWindow) =
      if (window.msgs.isEmpty) (0, cursor.getCount - 1)
      else if (window.msgs.head.time >= time) (0, window.offset - 1)
      else (window.offset + window.msgs.size, cursor.getCount - 1)

    windowLoader.currentWindow.indexOf(time)(ordering) match {
      case index if index >= 0 => Future.successful(index)
      case _ =>
        val (from, to) = cursorSearchBounds(windowLoader.currentWindow)
        if (to < from) Future.successful(-1) else cursorSearch(from, to)
    }
  }

  def prefetch(index: Int): Future[Unit] = windowLoader(index) flatMap { prefetch }

  def prefetch(window: IndexWindow): Future[Unit] = Future(window.msgs.iterator.filter(m => messages.get(m.id) == null).map(_.id).toVector) flatMap { ids =>
    if (ids.isEmpty) {
      verbose(l"prefetch at offset ${window.offset} unnecessary")
      Future.successful(())
    } else {
      val time = System.nanoTime()
      loader(ids) .map { ms =>
        ms foreach { m => putMessage(m) }
        verbose(l"pre-fetched ${ids.size} ids, got ${ms.size} msgs, for window offset: ${window.offset} in: ${(System.nanoTime() - time) / 1000 / 1000f} ms")
      } (Threading.Background) // LruCache is thread-safe, this mustn't be blocked by UI processing
    }
  }

  private var prevWindow = new IndexWindow(0, IndexedSeq.empty)

  private def loadWindow(index: Int, count: Int = 1) = {
    Threading.assertUiThread()
    require(index >= 0 && index + count <= size, s"loadWindow($index, $count) called on cursor with size: $size")

    val windowFuture = windowLoader(index, count)

    windowFuture.value match {
      case Some(Success(result)) => result
      case _ => logTime(l"loading window for index: $index")(Try(Await.result(windowFuture, 5.seconds)).getOrElse(new IndexWindow(index, IndexedSeq.empty)))
    }
  }

  /** Returns message at given index, will block if message data is not yet available for given index. */
  override def apply(index: Int): MessageAndLikes = { // implementation note: avoid all allocations on the happy path
    val window = loadWindow(index)

    if (! window.contains(index)) {
      MessageAndLikes.Empty
    } else {
      val fetching = if (prevWindow != window) {
        verbose(l"prefetching at $index, offset: ${window.offset}")
        prevWindow = window
        prefetch(window)
      } else futureUnit

      val id = window(index).id

      val msg = messages.get(id)
      if (msg ne null) msg else {
        logTime(l"waiting for window to prefetch")(Try(Await.result(fetching, 5.seconds)))
        Option(messages.get(id)).getOrElse {
          logTime(l"loading message for id: $id, position: $index") {
            val m = Try(Await.result(loader(Seq(id)), 500.millis).headOption).toOption.flatten
            m.foreach(putMessage)
            m.getOrElse(MessageAndLikes.Empty)
          }
        }
      }
    }
  }

  def getEntries(offset: Int, count: Int): Seq[Entry] = {
    val end = math.min(size, offset + count)
    val window = loadWindow(offset, math.min(count, WindowSize))

    window.msgs.slice(offset - window.offset, end - window.offset)
  }

  private def cursorLinearSearch(time: RemoteInstant, from: Int = 0, to: Int = cursor.getCount - 1): Int =
    if (from == 0) {
      new CursorIterator(cursor)(Entry.EntryReader).indexWhere(e => ordering.compare(e.time, time) >= 0)
    } else {
      val indexFromEnd = new ReverseCursorIterator(cursor)(Entry.EntryReader).indexWhere(e => ordering.compare(e.time, time) <= 0)
      if (indexFromEnd < 0) -1 else cursor.getCount - indexFromEnd - 1
    }

  private def cursorBinarySearch(time: RemoteInstant, from: Int = 0, to: Int = cursor.getCount - 1): Int = {
    val idx = from + (to - from - 1) / 2
    cursor.moveToPosition(idx)
    ordering.compare(Entry(cursor).time, time) match {
      case 0 => idx
      case c if c != 0 && from == to => -1
      case c if c > 0 => cursorBinarySearch(time, from, idx)
      case _ => cursorBinarySearch(time, idx + 1, to)
    }
  }

  private def putMessage(message: MessageAndLikes) = {
    messages.put(message.message.id, message)
    message.quote.foreach { q =>
      val qs = Option(quotes.get(q.id)).getOrElse(Seq())
      quotes.put(q.id, qs :+ message.message.id)
    }
  }
}

object MessagesCursor {
  val WindowSize = 256
  val WindowMargin = WindowSize / 4
  val futureUnit = Future.successful(())

  val Ascending = implicitly[Ordering[RemoteInstant]]
  val Descending = Ascending.reverse

  val Empty: MsgCursor = new MsgCursor {
    override val size: Int = 0
    override val lastReadIndex: Int = 0
    override def lastReadTime: RemoteInstant = RemoteInstant.Epoch
    override def indexOf(time: RemoteInstant): Int = -1
    override def apply(index: Int): MessageAndLikes = throw new IndexOutOfBoundsException(s"invalid index $index in empty message cursor")
    override def close(): Unit = ()
  }

  case class Entry(id: MessageId, time: RemoteInstant) {
    def <(e: Entry) = Entry.Order.compare(this, e) < 0
  }

  object Entry {
    val Empty = new Entry(MessageId(""), RemoteInstant.Epoch)

    implicit object Order extends Ordering[Entry] {
      override def compare(x: Entry, y: Entry): Int = {
        if (x.time == y.time) Ordering.String.compare(x.id.str, y.id.str)
        else Ordering.Long.compare(x.time.toEpochMilli, y.time.toEpochMilli)
      }
    }

    implicit object EntryReader extends Reader[Entry] {
      override def apply(implicit c: DBCursor): Entry =
        Entry(MessageId(c.getString(0)), RemoteInstant.ofEpochMilli(c.getLong(1)))
    }

    def apply(c: DBCursor): Entry = EntryReader(c)
    def apply(m: MessageData): Entry = Entry(m.id, m.time)
  }
}

class WindowLoader(cursor: DBCursor)(implicit dispatcher: DispatchQueue) extends DerivedLogTag {
  import MessagesCursor._

  @volatile private[this] var window = IndexWindow.Empty
  @volatile private[this] var windowLoading = Future.successful(window)

  private val totalCount = cursor.getCount

  private def shouldRefresh(window: IndexWindow, index: Int, count: Int) =
    window == IndexWindow.Empty ||
      window.offset > 0 && index < window.offset + WindowMargin ||
      (window.offset + WindowSize < totalCount && index + count > window.offset + WindowSize - WindowMargin)

  private def fetchWindow(start: Int, end: Int) = {
    verbose(l"fetchWindow($start, $end)")

    val items = (start until end) map { pos =>
      if (cursor.moveToPosition(pos)) Entry(cursor) else {
        error(l"can not move cursor to position: $pos, requested fetchWindow($start, $end)")
        Entry.Empty
      }
    }
    IndexWindow(start, items)
  }

  private def loadWindow(index: Int, count: Int) = windowLoading .recover { case _ => window } .map {
    case w if shouldRefresh(w, index, count) =>
      val start = math.max(0, math.min(cursor.getCount - WindowSize, index + count / 2 - WindowSize / 2))
      val end = math.min(cursor.getCount, start + MessagesCursor.WindowSize)
      window = fetchWindow(start, end)
      window
    case w => w
  }

  /**
    * Loads window containing element at given index and at least `minCount` following elements.
    */
  def apply(index: Int, minCount: Int = 1): Future[IndexWindow] = {
    require(minCount <= MessagesCursor.WindowSize)

    if (shouldRefresh(window, index, minCount)) {
      verbose(l"shouldRefresh($index) = true   offset: ${window.offset}")
      windowLoading = loadWindow(index, minCount)
    }

    if (window.contains(index) && window.contains(index + minCount - 1)) Future.successful(window)
    else {
      verbose(l"window doesn't contain all: $index - $minCount")
      windowLoading
    }
  }

  def currentWindow = window
}

case class IndexWindow(offset: Int, msgs: IndexedSeq[Entry]) {

  def contains(index: Int) = index >= offset && index < offset + msgs.size

  def apply(pos: Int) = msgs(pos - offset)

  def indexOf(time: RemoteInstant)(implicit ord: Ordering[RemoteInstant]) = msgs.binarySearch(time, _.time)(ord) match {
    case Found(n) => n + offset
    case InsertionPoint(n) => if (n == 0 || n == msgs.size) -1 else n + offset
  }
}

object IndexWindow {
  val Empty = new IndexWindow(-1, IndexedSeq.empty)
}
