/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.messages

import android.support.v7.widget.RecyclerView
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.content.ConvMessagesIndex._
import com.waz.content.{ConvMessagesIndex, MessagesCursor}
import com.waz.model.{ConvId, MessageData, MessageId}
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.{EventContext, Signal, Subscription}
import com.waz.zclient.messages.ItemAnimator.{LikesChanged, Payload}
import com.waz.zclient.{Injectable, Injector}
import org.threeten.bp.Instant

import scala.collection.Searching.{Found, InsertionPoint}
import scala.collection.mutable.ListBuffer

class RecyclerCursor(val conv: ConvId, zms: ZMessaging, adapter: RecyclerView.Adapter[_])(implicit inj: Injector, ev: EventContext) extends Injectable { self =>

  import com.waz.threading.Threading.Implicits.Ui

  verbose(s"RecyclerCursor created for conv: $conv")

  val storage = zms.messagesStorage
  val likes = zms.reactionsStorage

  val index = storage.msgsIndex(conv)
  val lastReadTime = Signal.future(index).flatMap(_.signals.lastReadTime)
  val countSignal = Signal[Int](0)

  private val window = new IndexWindow()
  private var closed = false
  private var history = Seq.empty[ConvMessagesIndex.Change]
  private var cursor = Option.empty[MessagesCursor]
  private var subs = Seq.empty[Subscription]
  private var onChangedSub = Option.empty[Subscription]

  index onSuccess { case idx =>
    verbose(s"index: $idx, closed?: $closed")
    if (!closed) {
      subs = Seq(
        idx.signals.messagesCursor.on(Threading.Ui) { setCursor },
        idx.signals.indexChanged.on(Threading.Ui) { change => history = history :+ change }
      )
    }
  }

  def close() = {
    Threading.assertUiThread()
    closed = true
    cursor = None
    subs.foreach(_.destroy())
    onChangedSub.foreach(_.destroy())
    subs = Nil
    history = Nil
    adapter.notifyDataSetChanged()
    countSignal ! 0
  }

  private def setCursor(c: MessagesCursor) = {
    verbose(s"setCursor: c: $c, count: ${c.size}")
    if (!closed) {
      self.cursor = Some(c)
      notifyFromHistory(c.createTime)
      countSignal ! c.size
      onChangedSub.foreach(_.destroy())
      onChangedSub = Some(c onUpdate (id => likesChanged(Seq(id))))
    }
  }

  private def notifyFromHistory(time: Instant) = {
    verbose(s"notifyFromHistory($time)")
    val (toApply, toLeave) = history.partition(_.time <= time)
    history = toLeave

    verbose(s"history: $toApply")
    toApply foreach {
      case Added(msgs) => msgs foreach window.onAdded // TODO: batching (use notifyRange if possible)
      case Removed(msg) => window.onRemoved(msg)
      case Updated(updates) => updates foreach { case (prev, current) => window.onUpdated(prev, current, null) }
      case RemovedOlder(t) => window.onRemoved(t)
    }

    if (toApply.isEmpty) adapter.notifyDataSetChanged()
  }

  private def likesChanged(ids: Seq[MessageId]) = {
    verbose(s"likesChanged: $ids")
    storage.getAll(ids).map { msgs =>
      msgs foreach {
        _ foreach { msg => window.onUpdated(msg, msg, LikesChanged) }
      }
    }(Threading.Ui)
  }

  def count: Int = cursor.fold(0)(_.size)

  def apply(position: Int): MessageAndLikes = cursor.fold2(null, { c =>
    if (window.shouldReload(position)) {
      verbose(s"reloading window at position: $position")
      window.reload(c, position)
    }

    val msg = c(position)
    verbose(s"Fetching for position: $position message: $msg")
    msg
  })

  def lastReadIndex() = cursor.fold(-1)(_.lastReadIndex)

  class IndexWindow {
    import MessagesCursor.Entry

    private var offset = 0

    def getOffset = offset

    //just for printing
    private var entries = new ListBuffer[Entry]()

    def shouldReload(position: Int): Boolean = offset > math.max(0, position - 25) || offset + entries.length < math.min(count, position + 25)

    def reload(c: MessagesCursor, position: Int) = {
      offset = math.max(0, position - 50)
      entries.clear()
      entries ++= c.getEntries(offset, math.min(count - offset, 100))
    }

    private def search(e: Entry) = entries.toIndexedSeq.binarySearch(e, identity)

    def clear() = {
      offset = 0
      entries.clear()
      adapter.notifyDataSetChanged()
    }

    def onRemoved(upTo: Instant) =
      search(Entry(MessageId("{}"), upTo)) match { // using `{}` as MessageId.MAX value
        case InsertionPoint(ind) if ind < 0 && ind < entries.length =>
          entries.remove(0, ind)
          adapter.notifyItemRangeRemoved(0, offset + ind)
        case _ =>
          // removed all or no items from window, need to reload data set
          clear()
      }

    def onRemoved(msg: MessageData) =
      search(Entry(msg)) match {
        case InsertionPoint(0) => // outside of window
          offset -= 1
          adapter.notifyItemRemoved(0)
        case InsertionPoint(ind) if ind == entries.length =>
          verbose(s"notifyRemoved ${offset + ind} (outside of window)")
          adapter.notifyItemRemoved(offset + ind)
        case InsertionPoint(_) =>
          warn(s"onRemoved($msg) - message not found in current window")
          clear()
        case Found(ind) =>
          entries.remove(ind, 1)
          verbose(s"notifyRemoved ${offset + ind}")
          adapter.notifyItemRemoved(offset + ind)
      }

    def onAdded(msg: MessageData) = {
      val entry = Entry(msg)
      search(entry) match {
        case InsertionPoint(0) => // outside of window
          offset += 1
          verbose(s"notifyInserted 0")
          adapter.notifyItemInserted(0)
        case InsertionPoint(ind) if ind == entries.length =>
          verbose(s"notifyInserted ${offset + entries.length}")
          adapter.notifyItemInserted(offset + entries.length)
        case InsertionPoint(ind) =>
          entries.insert(ind, entry)
          verbose(s"notifyInserted ${offset + ind}")
          adapter.notifyItemInserted(offset + ind)
        case Found(ind) =>
          warn(s"onAdded($msg) - message already exists in current window")
          entries.update(ind, entry)
          adapter.notifyItemChanged(offset + ind)
      }
    }

    def onUpdated(prev: MessageData, current: MessageData, payload: Payload) = {
      val pe = Entry(prev)
      val ce = Entry(current)

      if (pe == ce) {
        verbose(s"position unchanged, will only notify adapter about data change")
        search(pe) match {
          case Found(pos) =>
            verbose(s"found, notifiying adapter at pos: ${offset + pos}")
            adapter.notifyItemChanged(offset + pos, payload)
          case _ => verbose("no need to notify about changes outside of window")
        }
      } else {
        verbose("message position changed")
        val len = entries.length

        (search(pe), search(ce)) match {
          case (InsertionPoint(0), InsertionPoint(0)) | (InsertionPoint(`len`), InsertionPoint(`len`)) =>
            // message updated outside of window, ignoring
          case (Found(src), InsertionPoint(dst)) if src == dst || src == dst - 1 =>
            // time changed, but position remains the same
            entries.update(src, ce)
            adapter.notifyItemChanged(offset + src, payload)
          case (Found(src), InsertionPoint(dst)) =>
            entries.remove(src)
            val target = if (dst > src) dst - 1 else dst // use dst - 1 since one item was just removed on left side
            entries.insert(target, ce)
            adapter.notifyItemMoved(src + offset, target + offset)
          case (InsertionPoint(src), InsertionPoint(target)) =>
            entries.insert(target, ce)
            adapter.notifyItemMoved(src + offset, target + offset)
        }
      }
    }
  }
}

