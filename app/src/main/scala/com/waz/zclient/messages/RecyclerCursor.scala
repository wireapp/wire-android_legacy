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

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.MessageFilter
import com.waz.content.ConvMessagesIndex._
import com.waz.content.{ConvMessagesIndex, MessagesCursor}
import com.waz.model.{ConvId, MessageData}
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.{EventContext, Signal, Subscription}
import com.waz.zclient.messages.RecyclerCursor.RecyclerNotifier
import com.waz.zclient.{Injectable, Injector}
import org.threeten.bp.Instant

import scala.concurrent.Future

class RecyclerCursor(val conv: ConvId, zms: ZMessaging, val adapter: RecyclerNotifier, val messageFilter: Option[MessageFilter] = None)(implicit inj: Injector, ev: EventContext) extends Injectable { self =>

  import Threading.Implicits.Ui

  verbose(s"RecyclerCursor created for conv: $conv")

  val countSignal = Signal[Int]()
  val cursorLoaded = Signal[Boolean](false)

  private val window = new IndexWindow(this, adapter)
  private val closed = Signal(false)
  private val cursor = Signal(Option.empty[MessagesCursor])
  private var subs = Seq.empty[Subscription]
  private var onChangedSub = Option.empty[Subscription]

  // buffer storing all update notifications which are replayed when cursor is reloaded
  // this is needed to notify list about updates which happened while cursor was being loaded
  // without that we could miss some updates due to race conditions
  private var history = Seq.empty[ConvMessagesIndex.Updated]

  for {
    idx <- messageFilter.fold(zms.messagesStorage.msgsIndex(conv))(f => zms.messagesStorage.msgsFilteredIndex(conv, f))
    cl <- closed.head
  } yield if (!cl) {
    subs = Seq(
      idx.signals.messagesCursor.on(Threading.Ui) { setCursor },
      idx.signals.indexChanged.on(Threading.Ui) {
        case u: Updated => history = history :+ u
        case _ => // ignore other changes
      }
    )
  }

  def close() = {
    Threading.assertUiThread()
    closed ! true
    cursor ! None
    subs.foreach(_.destroy())
    onChangedSub.foreach(_.destroy())
    subs = Nil
    history = Nil
    adapter.notifyDataSetChanged()
    countSignal ! 0
  }

  private def setCursor(c: MessagesCursor): Unit = {
    verbose(s"setCursor: c: $c, count: ${c.size}")
    self.cursor ! Some(c)
    cursorLoaded ! true
    window.cursorChanged(c)
    notifyFromHistory(c.createTime)
    countSignal ! c.size
    onChangedSub.foreach(_.destroy())
    onChangedSub = Some(c.onUpdate.on(Threading.Ui) { case (prev, current) => onUpdated(prev, current) })
  }

  private def notifyFromHistory(time: Instant): Unit = {
    verbose(s"notifyFromHistory($time)")

    history.foreach { _.updates foreach { case (prev, current) => window.onUpdated(prev, current) } }
    history = history.filter(_.time.isAfter(time)) // leave only updates which happened after current cursor was loaded
  }

  private def onUpdated(prev: MessageAndLikes, current: MessageAndLikes): Unit =
    window.onUpdated(prev.message, current.message)

  def count: Int = cursor.currentValue.flatMap(_.map(_.size)).getOrElse(0)

  def apply(position: Int): MessageAndLikes = cursor.currentValue.getOrElse(None).fold2(null, { c =>
    if (window.shouldReload(position)) {
      verbose(s"reloading window at position: $position")
      window.reload(c, position)
    }

    c(position)
  })

  def lastReadIndex: Int = cursor.currentValue.flatMap(_.map(_.lastReadIndex)).getOrElse(-1)

  def positionForMessage(messageData: MessageData): Future[Int] =
    cursor.collect { case Some(c) => c } .head.flatMap(_.asyncIndexOf(messageData.time, binarySearch = true))
}

object RecyclerCursor {

  trait RecyclerNotifier {
    def notifyDataSetChanged(): Unit
    def notifyItemRangeInserted(index: Int, length: Int): Unit
    def notifyItemRangeRemoved(pos: Int, count: Int): Unit
    def notifyItemRangeChanged(index: Int, length: Int): Unit
  }
}
