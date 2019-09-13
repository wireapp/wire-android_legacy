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
package com.waz.service.downloads

import com.waz.api.ProgressIndicator.State
import com.waz.api.impl.ProgressIndicator.ProgressData
import com.waz.cache.CacheEntry
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{AssetData, AssetId}
import com.waz.service.ZMessaging.clock
import com.waz.service.downloads.AssetLoader.DownloadException
import com.waz.threading.CancellableFuture.CancelException
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils.events._
import com.waz.utils.{Backoff, ExponentialBackoff, returning}
import org.threeten.bp.Instant

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal

/**
  * Keeps track of all load requests in priority queue, executes more important loads first.
  * Priority is based on request start times, newer requests are considered more important.
  *
  * Note, the AssetLoaderService is globally defined, and different instances of AssetLoader can be used to perform load
  * operations. This is to have a single global asset loading queue for all zms instances (and also for no instances), but
  * it still allows scoped instances of the actual loaders to be used (with their relevant credentials or lack thereof)
  */
class AssetLoaderService extends DerivedLogTag {
  import AssetLoaderService._
  private implicit val dispatcher = new SerialDispatchQueue(name = "AssetLoaderService")
  private implicit val ev = EventContext.Global

  private val requests  = new mutable.HashMap[AssetId, LoadEntry]()
  private val active    = new mutable.HashSet[AssetId]()
  private val queue     = new mutable.PriorityQueue[QueueEntry]()(QueueOrdering)

  private val onAdded   = EventStream[LoadEntry]()

  private def getLoadEntry(id: AssetId): Signal[Option[LoadEntry]] =
    new AggregatingSignal[LoadEntry, Option[LoadEntry]](onAdded.filter(_.asset.id == id), Future(requests.get(id)), { (_, added) => Some(added) })

  def getLoadProgress(id: AssetId): Signal[ProgressData] = getLoadEntry(id).flatMap {
    case Some(entry) =>
//      verbose(s"getLoadProgress: $id")
      entry.state
    case None => Signal(ProgressData.Unknown)
  }

  def cancel(id: AssetId): Future[Unit] = Future(removeTaskIfIdle(id))

  //When cancelling tasks, it only really makes sense to cancel idle loads, or else we'll be wasting work.
  //This also helps prevent race conditions caused by Cancellable futures exposing the work done by the LoadEntry
  private def removeTaskIfIdle(id: AssetId) = {
    if (!active.contains(id)) {
//      verbose(s"Cancelling idle task: $id")
      updateQueue(toRemove = Some(id))
      requests.get(id).foreach(_.cancel())
      true
    } else false
  }

  def load(asset: AssetData, force: Boolean = false)(implicit loader: AssetLoader): CancellableFuture[Option[CacheEntry]] =
    loadRevealAttempts(asset, force).map { case (res, _) => res}

  //reveals attempted load count - useful for testing retry logic
  def loadRevealAttempts(asset: AssetData, force: Boolean = false)(implicit loader: AssetLoader): CancellableFuture[(Option[CacheEntry], Int)] = {
//    verbose(s"loadRevealAttempts: ${asset.id}")

    val loadEntry =
      updateQueue(toAdd = Some(asset, loader), force = force)
      .getOrElse(throw new IllegalArgumentException(s"Failed to load asset ${asset.id} with force = $force"))

    new CancellableFuture(loadEntry.promise) {
      override def cancel()(implicit tag: LogTag) =
        returning(removeTaskIfIdle(asset.id)) { removed =>
//          verbose(s"Tried to cancel loadEntry: ${asset.id}: removed?: $removed")(tag)
        }
    }.map {
      case (entry, attempts) => (Some(entry), attempts)
    }
  }

  // All operations modifying the queue and associated data structures (active, requests) has to be performed inside this method
  // or in methods which are called only through this one. Outside updateQueue only getters are allowed.
  private def updateQueue(toAdd: Option[(AssetData, AssetLoader)] = None,
                          toRemove: Option[AssetId] = None,
                          force: Boolean = false): Option[LoadEntry] = synchronized {
//    verbose(s"updateQueue(${toAdd.map(_._1.id)}, $toRemove, $force)")
//    verbose(s"load requests: ${requests.keys.map(_.toString).toSeq.sorted}")
//    verbose(s"active:        ${active.map(_.toString).toSeq.sorted}")

    val ret = toAdd.map { case (asset, loader) =>
      requests.getOrElse(asset.id,
        if (!active.contains(asset.id)) {
//          verbose(s"adding entry to the queue: ${asset.id}")
          returning(LoadEntry(asset, loader, force)) { entry =>
            requests += asset.id -> entry
            queue.enqueue(entry.queuePlaceHolder)
            onAdded ! entry
          }
        } else throw new Exception("Active load operation was missing from request map")
      )
    }

    toRemove.foreach { id =>
      requests -= id
      active -= id
    }

    if (queue.nonEmpty && active.size < MaxConcurrentLoadRequests) {
      val id = queue.dequeue().id
      requests.get(id).fold(() /*verbose(s"De-queued load entry: $id has been cancelled - discarding")*/) { entry =>
        if (active.add(id)) {
//          verbose(s"starting load for $entry")
          load(entry).onComplete { _ => updateQueue(toRemove = Some(id)) } //check queue again in case we're blocked and waiting (in which case we won't reach the next checkQueue call)
        } else {
//          verbose(s"entry: $id was already active, doing nothing")
        }
      }
      Future { updateQueue() } //effectively causes a while(queue.nonEmpty && active.size < MaxConcurrentLoadRequests)
    }

    ret
  }

  //Returns a Future, since once actual loading has started, it doesn't make sense to cancel it and waste the work.
  private def load(entry: LoadEntry): Future[(CacheEntry, Int)] = {
    val id = entry.asset.id

    def onFail(ex: Throwable, attempts: Int = 1) = {
      ex match {
        case _: CancelException =>
//          error(s"Loading cancelled for $id after $attempts attempts", ex)
          requests.get(id).foreach(_.state ! ProgressData(0, 0, State.CANCELLED))
        case NonFatal(_) =>
//          error(s"Loading failed for $id after $attempts attempts", ex)
          requests.get(id).foreach(_.state ! ProgressData(0, 0, State.FAILED))
      }
      Future.failed(ex)
    }

    def recursive(retries: Int = 0): Future[(CacheEntry, Int)] = {
      val delay =
        if (retries == 0) CancellableFuture.successful({})
        else if (retries > AssetLoaderService.backoff.maxRetries) throw new Exception(MaxRetriesErrorMsg)
        else CancellableFuture.delay(AssetLoaderService.backoff.delay(retries))

      delay.future.flatMap { _ =>
        entry.load().future.map { res =>
//          verbose(s"Loading succeeded for: $id")
          requests.get(id).foreach(_.state ! ProgressData(0, 0, State.COMPLETED))
          (res, retries + 1)
        }
      }
    }.recoverWith {
      case ex: DownloadException if ex.isRecoverable => recursive(retries + 1)
      case NonFatal(ex)                              => onFail(ex, attempts = retries + 1)
    }

    entry.state ! ProgressData(0, 0, State.RUNNING)
    entry.promise.tryCompleteWith(recursive()).future
  }
}

object AssetLoaderService {

  val MaxRetriesErrorMsg = "Max retries for loading asset exceeded"

  val MaxConcurrentLoadRequests = 4
  val DefaultExpiryTime = 7.days

  //var for tests
  var backoff: Backoff = new ExponentialBackoff(250.millis, 7.days)

  private[downloads] case class LoadEntry(asset:    AssetData,
                                          loader:   AssetLoader,
                                          force:    Boolean,
                                          promise:  Promise[(CacheEntry, Int)] = Promise[(CacheEntry, Int)](),
                                          state:    SourceSignal[ProgressData] = Signal(ProgressData.Unknown),
                                          time:     Instant                    = clock.instant()
                                         ) {
    def cancel(): Unit =
      promise.tryFailure(new CancelException("Cancelled by user"))

    def load() = {
//      verbose(s"performing load: ${asset.id}, name: ${asset.name}")
      loader.loadAsset(asset, state ! _, force)
    }

    override def toString: String = s"LoadEntry(${asset.id}) { force: $force, state: $state, time: $time }"

    lazy val queuePlaceHolder: QueueEntry = QueueEntry(asset.id, time)
  }

  case class QueueEntry(id: AssetId, time: Instant)

  implicit object QueueOrdering extends Ordering[QueueEntry] {
    override def compare(x: QueueEntry, y: QueueEntry): Int =
      Ordering.ordered[Instant].compare(x.time, y.time)
  }
}
