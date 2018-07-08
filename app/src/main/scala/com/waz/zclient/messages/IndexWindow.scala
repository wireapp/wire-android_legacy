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

import com.waz.ZLog._
import com.waz.ZLog.ImplicitTag._
import com.waz.content.MessagesCursor
import com.waz.content.MessagesCursor.Entry
import com.waz.model.MessageData
import com.waz.zclient.messages.RecyclerCursor.RecyclerNotifier
import com.waz.utils._

import scala.collection.Searching.Found
import scala.collection.mutable.ArrayBuffer

/**
  * Index maintaining message indices for currently displayed messages.
  *
  * RecyclerView wants to get notification about data set changes with list position.
  * Our storage on the other hand produces events containing actual messages,
  * we need to maintain an index to know in which position given messages are displayed.
  *
  * RecyclerView only cares about notifications for visible elements, so it's enough to
  * keep a small window around current position, and ignore changes outside of it.
  */
class IndexWindow(cursor: RecyclerCursor, notifier: RecyclerNotifier, size: Int = 100) {
  import IndexWindow._

  private val ord = implicitly[Ordering[Entry]]

  private var data = IndexedSeq.empty[Entry]
  private var totalCount = 0

  private var offset = 0

  def shouldReload(position: Int): Boolean = offset > math.max(0, position - 25) || offset + data.length < math.min(cursor.count, position + 25)

  // moves window to specified position, this doesn't generate any notifications, as underlying data didn't really change
  def reload(c: MessagesCursor, position: Int): Unit = {
    offset = math.max(0, position - 50)
    data = c.getEntries(offset, math.min(cursor.count - offset, 100)).toIndexedSeq
    val size = c.size
    if (size != totalCount) {
      totalCount = size
      error("MessagesCursor size has changed unexpectedly, will notify data set change.")
      notifier.notifyDataSetChanged()
    }
  }

  private def search(e: Entry) = data.binarySearch(e, identity)

  def clear() = {
    offset = 0
    data = IndexedSeq.empty
    notifier.notifyDataSetChanged()
  }

  def onUpdated(prev: MessageData, current: MessageData) =
    search(Entry(current)) match {
      case Found(pos) =>
        verbose(s"found, notifying adapter at pos: ${offset + pos}")
        notifier.notifyItemRangeChanged(offset + pos, 1)
      case _ =>
        verbose("no need to notify about changes outside of window")
    }

  /**
    * Reloads index and notifies recycler about data changes.
    * This is called when cursor is reloaded due to some data change.
    * We need to compare old index with new data to find out if any messages were added or removed.
    * Most of the time our cursor will have only small change (one addition or removal), we don't try to be exact here,
    * RecyclerView expects to receive just one notification anyway, so we will fall back to generic notifyItemRangeChanged
    * in case of more complicated changes, will notify with range encompassing all changes.
    *
    * We also track total count of messages to detect any additions or removals outside of current window.
    * Will report full data set change if deduced changes doen't add up with total count, this is needed to avoid inconsistencies in recycler view.
    *
    * XXX: This doesn't work well when message is moved, it will just update all messages in range of move.
    * We might want to improve that, although we rarely move messages, only in case of race conditions,
    * there is no intended use case for moving of message.
    */
  def cursorChanged(c: MessagesCursor) = {
    val items = if (cursor.count > 0) c.getEntries(offset, math.min(cursor.count - offset, 100)).toIndexedSeq else IndexedSeq.empty
    val prevCount = totalCount
    val change = diff(data, items).result
    data = items
    totalCount = c.size
    val count = math.min(change.count, cursor.count - offset - change.index)
    if (count > 5 && count > items.size / 2) {
      // revert to reporting full data set change if detected change is big (most of the items, excluding small windows)
      notifier.notifyDataSetChanged()
    } else change match {
      case Change.None if prevCount == totalCount => // nothing really changed
      case Change.Added(index, _) if prevCount + count == totalCount =>
        notifier.notifyItemRangeInserted(offset + index, count)
      case Change.Removed(index, _) if prevCount - count == totalCount =>
        notifier.notifyItemRangeRemoved(offset + index, count)
      case Change.Changed(index, _) if prevCount == totalCount =>
        notifier.notifyItemRangeChanged(offset + index, count)
      case _ =>
        // report full data set change if detected change doesn't add up with total count
        notifier.notifyDataSetChanged()
    }
  }

  private def diff(from: Seq[Entry], to: Seq[Entry], index: Int = 0, builder: ChangeBuilder = new ChangeBuilder): ChangeBuilder = (from.headOption, to.headOption) match {
    case (None, None)                           => builder
    case (Some(_), None)                        => builder.remove(index, from)
    case (None, Some(_))                        => builder.add(index, to)
    case (Some(fh), Some(th)) if fh.id == th.id => diff(from.tail, to.tail, index + 1, builder)
    case (Some(fh), Some(th)) if ord.gt(fh, th) => diff(from, to.tail, index + 1, builder.add(index, th))
    case (Some(fh), Some(_))                    => diff(from.tail, to, index, builder.remove(index, fh))
  }
}

object IndexWindow {

  sealed trait Change {
    val index: Int
    val count: Int
  }
  object Change {
    case class Added(index: Int, count: Int) extends Change
    case class Removed(index: Int, count: Int) extends Change
    case class Changed(index: Int, count: Int) extends Change
    case object None extends Change {
      val index = 0
      val count = 0
    }
  }

  class ChangeBuilder() {
    import Change._
    val diffs = new ArrayBuffer[Change]

    private def add(index: Int, count: Int): ChangeBuilder = diffs.lastOption match {
      case Some(Added(idx, c)) if index == idx + c + 1 =>
        diffs.update(diffs.size - 1, Added(idx, c + count))
        this
      case _ =>
        diffs += Added(index, count)
        this
    }

    private def remove(index: Int, count: Int): ChangeBuilder = diffs.lastOption match {
      case Some(Removed(idx, c)) if index == idx =>
        diffs.update(diffs.size - 1, Removed(idx, count + c))
        this
      case _ =>
        diffs += Removed(index, count)
        this
    }

    def add(index: Int, item: Entry): ChangeBuilder = add(index, 1)

    def add(index: Int, items: Seq[Entry]): ChangeBuilder = add(index, items.size)

    def remove(index: Int, item: Entry): ChangeBuilder = remove(index, 1)

    def remove(index: Int, items: Seq[Entry]): ChangeBuilder = remove(index, items.size)

    def result =
      if (diffs.isEmpty) Change.None
      else if (diffs.size == 1) diffs.head
      else {
        val head = diffs.head
        val last = diffs.last
        Changed(head.index, last.index + last.count - head.index)
      }
  }
}
