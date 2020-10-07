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
package com.waz.ui

import androidx.collection.LruCache
import com.waz.CacheLike
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.threading.Threading._
import com.waz.utils.ThrottledProcessingQueue
import com.wire.signals.{EventContext, EventStream, Subscription}

import scala.collection._
import scala.concurrent.Future
import scala.ref.{ReferenceQueue, WeakReference}

class UiCache[Key, A <: AnyRef](lruSize: Int = 0)(implicit ui: UiModule)
  extends CacheLike[Key, A] with DerivedLogTag {

  val queue = new ReferenceQueue[A]
  val lru = new LruCache[Key, A](lruSize max 1)
  val items = new mutable.HashMap[Key, WeakReference[A]]

  ui.onReset.onUi(_ => clear())

  def get(k: Key): Option[A] = {
    Threading.assertUiThread()
    Option(getOrNull(k))
  }

  def sizeString = s"lru: ${lru.size}, items: ${items.size}"

  def getOrNull(k: Key): A = {
    dropQueue()
    if (lruSize > 0) {
      val item = lru.get(k)
      if (item ne null) item
      else if (items contains k) items(k).underlying.get
      else null.asInstanceOf[A]
    } else {
      val res = if (items contains k) items(k).underlying.get else null.asInstanceOf[A]
      if (res ne null) lru.put(k, res) // update lru
      res
    }
  }

  /**
   * Returns cached item without changes to LRU cache.
   */
  def peek(k: Key): Option[A] = {
    Threading.assertUiThread()
    dropQueue()
    items.get(k).flatMap(_.get)
  }

  def put(k: Key, item: A): Unit = {
    Threading.assertUiThread()
    dropQueue()
    if (lruSize > 0) lru.put(k, item)
    items.put(k, new WeakReferenceWithKey(k, item, queue))
  }

  def update(k: Key, update: A => Unit, default: => A): A = {
    val item = get(k).fold { default } { v => update(v); v }
    put(k, item)
    item
  }

  def clear(): Unit = {
    lru.evictAll()
    items.clear()
    dropQueue()
  }

  def foreach(f: A => Unit) = items.foreach {
    case (_, WeakReference(item)) => f(item)
    case _ =>
  }

  private def dropQueue(): Unit = queue.poll match {
    case Some(ref: WeakReferenceWithKey) =>
      remove(ref)
      dropQueue()
    case Some(ref) =>
      error(s"unexpected ref: $ref")
      dropQueue()
    case _ =>
  }

  private def remove(ref: WeakReferenceWithKey): Unit = items.remove(ref.key) match {
    case Some(r) if r != ref => items.put(ref.key, r) // removed different reference with same key, this means that the cache has already been updated and we shouldn't remove this item
    case _ =>
  }

  class WeakReferenceWithKey(val key: Key, item: A, queue: ReferenceQueue[A]) extends WeakReference[A](item, queue)
}

trait UiCached[A, Key, Data] {
  // updates item from storage - used when UI is resumed to make sure item is not stale
  def reload(item: A): Unit

  def update(item: A, d: Data): Unit

  /**
   * Returns a map of values to update in cache (removes duplicates).
   */
  def toUpdateMap(values: Seq[Data]): Map[Key, Data]
}

class UiCacheUpdater[A <: AnyRef, Key, Data](cache: UiCache[Key, A], events: ZMessaging => EventStream[Data])(implicit cached: UiCached[A, Key, Data], val ui: UiModule) extends UiEventListener[Data] {

  override protected def publisher(zms: ZMessaging): EventStream[Data] = events(zms)

  override protected def process(events: Seq[Data]): Future[Unit] = {
    val updated = cached.toUpdateMap(events)
    Future {
      updated foreach { case (key, value) =>
        cache.peek(key).foreach { c => cached.update(c, value) }
      }
    } (Threading.Ui)
  }

  override protected def onReset: Future[Unit] = Threading.Ui { cache.clear() }

  override protected def onResume: Future[Unit] = Threading.Ui { cache.foreach(cached.reload) }
}

object UiCacheUpdater {
  def apply[A <: AnyRef, Key, Data](cache: UiCache[Key, A], events: ZMessaging => EventStream[Data])(implicit cached: UiCached[A, Key, Data], ui: UiModule) = new UiCacheUpdater(cache, events)
}

trait UiEventListener[A] {
  private var observer = Option.empty[Subscription]

  def ui: UiModule

  private implicit def ec = ui.eventContext

  // TODO: think about synchronizing all throttled changes (in all ui listeners) so that ui is notified about all changes at the same time
  // this should make ui updating more consistent (easier to start animations at the same time, less flicker), but may hurt performance (bigger update, bigger lag on ui)
  private val updateQueue = new ThrottledProcessingQueue[A](UiEventListener.UpdateThrottling, process, s"UiEventListenerQueue")

  protected def publisher(zms: ZMessaging): EventStream[A]
  
  protected def process(events: Seq[A]): Future[Unit]
  
  protected def onResume: Future[Unit]
  
  protected def onReset: Future[Unit]
  
  ui.onStarted {
    case true => updateQueue.post(onResume)
    case _ => // ignore
  }

  ui.currentZms {
    case Some(z) =>
      observer.foreach(_.destroy())
      observer = Some(publisher(z) { updateQueue ! _ })
    case None =>
      updateQueue.post(onReset)
      observer.foreach(_.destroy())
      observer = None
  }
}

object UiEventListener {
  import scala.concurrent.duration._
  val UpdateThrottling = 250.millis
}
