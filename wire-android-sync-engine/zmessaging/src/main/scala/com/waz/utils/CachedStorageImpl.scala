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
package com.waz.utils

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue}

import androidx.collection.LruCache
import com.waz.content.Database
import com.waz.db.DaoIdOps
import com.waz.log.BasicLogging.LogTag
import com.waz.model.errors.NotFoundLocal
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.ContentChange.{Added, Removed, Updated}
import com.waz.utils.events._
import com.waz.utils.wrappers.DB

import scala.collection.JavaConverters._
import scala.collection.generic._
import scala.collection.{GenTraversableOnce, Seq, breakOut, mutable}
import scala.concurrent.{ExecutionContext, Future}

trait StorageDao[K, V <: Identifiable[K]] {
  def getById(key: K)(implicit db: DB): Option[V]
  def getAll(keys: Set[K])(implicit db: DB): Seq[V]
  def list(implicit db: DB): Seq[V]
  def insertOrReplace(items: GenTraversableOnce[V])(implicit db: DB): Unit
  def deleteEvery(ids: GenTraversableOnce[K])(implicit db: DB): Unit
}

object StorageDao {

  implicit class DbDao[K, V <: Identifiable[K]](dao: DaoIdOps[V] { type IdVals = K }) extends StorageDao[K, V] {
    override def getById(key: K)(implicit db: DB): Option[V] = dao.getById(key)
    override def getAll(keys: Set[K])(implicit db: DB): Seq[V] = dao.getAll(keys)
    override def list(implicit db: DB) = dao.list
    override def deleteEvery(ids: GenTraversableOnce[K])(implicit db: DB): Unit = dao.deleteEvery(ids)
    override def insertOrReplace(items: GenTraversableOnce[V])(implicit db: DB): Unit = dao.insertOrReplace(items)
  }
}

trait Storage2[K, V <: Identifiable[K]] {
  //TODO Think about access modifiers
  implicit def ec: ExecutionContext

  def loadAll(keys: Set[K]): Future[Seq[V]]
  def saveAll(values: Iterable[V]): Future[Unit]
  def deleteAllByKey(keys: Set[K]): Future[Unit]

  def find(key: K): Future[Option[V]] = loadAll(Set(key)).map(_.headOption)
  def get(key: K): Future[V] = find(key).flatMap {
    case Some(value) => Future.successful(value)
    case None => Future.failed(NotFoundLocal(s"Entity with key = '$key' not found"))
  }
  def save(value: V): Future[Unit] = saveAll(List(value))
  def deleteByKey(key: K): Future[Unit] = deleteAllByKey(Set(key))
  def deleteAll(values: Iterable[V]): Future[Unit] = deleteAllByKey(values.map(_.id).toSet)
  def delete(value: V): Future[Unit] = deleteAll(List(value))
  def update(key: K, updater: V => V): Future[Option[(V, V)]] =
    find(key).flatMap {
      case None => Future.successful(None)
      case Some(value) =>
        val updated = updater(value)
        save(updated).map(_ => Some(value -> updated))
    }
}

trait ReactiveStorage2[K, V <: Identifiable[K]] extends Storage2[K, V] {
  def onAdded: EventStream[Seq[V]]
  def onUpdated: EventStream[Seq[(V, V)]]
  def onDeleted: EventStream[Set[K]]

  def onChanged(key: K): EventStream[V] =
    onUpdated
      .map(_.view.map { case (old, updated) => updated }.find(_.id == key))
      .collect { case Some(v) => v }

  def onRemoved(key: K): EventStream[K] =
    onDeleted.map(_.view.find(_ == key)).collect { case Some(k) => k }

  def optSignal(key: K): Signal[Option[V]] = {
    val changeOrDelete = onChanged(key).map(Option(_)).union(onRemoved(key).map(_ => Option.empty[V]))
    new AggregatingSignal[Option[V], Option[V]](changeOrDelete, find(key), { (_, v) => v })
  }

  def signal(key: K): Signal[V] =
    optSignal(key).collect { case Some(v) => v }
}

class DbStorage2[K, V <: Identifiable[K]](dao: StorageDao[K, V])
                     (implicit
                      override val ec: ExecutionContext,
                      db: DB) extends Storage2[K,V] {

  def loadAll: Future[Seq[V]] = Future(dao.list) //TODO Should we add this method to the Storage2 contract?
  override def loadAll(keys: Set[K]): Future[Seq[V]] = Future(dao.getAll(keys))
  override def saveAll(values: Iterable[V]): Future[Unit] = Future(dao.insertOrReplace(values))
  override def deleteAllByKey(keys: Set[K]): Future[Unit] = Future(dao.deleteEvery(keys))
}

class InMemoryStorage2[K, V <: Identifiable[K]](cache: LruCache[K, V])
                            (implicit
                             override val ec: ExecutionContext) extends Storage2[K, V] {

  override def loadAll(keys: Set[K]): Future[Seq[V]] = Future(keys.toSeq.flatMap(k => Option(cache.get(k))))
  override def saveAll(values: Iterable[V]): Future[Unit] = Future(values.foreach(v => cache.put(v.id, v)))
  override def deleteAllByKey(keys: Set[K]): Future[Unit] = Future(keys.foreach(cache.remove))
}

class CachedStorage2[K, V <: Identifiable[K]](main: Storage2[K, V], cache: Storage2[K, V])
                         (implicit
                          override val ec: ExecutionContext) extends Storage2[K, V] {

  override def loadAll(keys: Set[K]): Future[Seq[V]] =
    for {
      fromCache <- cache.loadAll(keys)
      fromMain <-
        if (keys.size == fromCache.size) Future.successful(Seq.empty)
        else {
          val cachedKeys = fromCache.map(_.id)
          val missingKeys = keys -- cachedKeys
          main.loadAll(missingKeys)
        }
    } yield {
      if (fromMain.nonEmpty) cache.saveAll(fromMain)
      fromCache ++ fromMain
    }

  override def saveAll(values: Iterable[V]): Future[Unit] =
    for {
      _ <- main.saveAll(values)
      _ <- cache.saveAll(values)
    } yield ()

  override def deleteAllByKey(keys: Set[K]): Future[Unit] = {
    for {
      _ <- main.deleteAllByKey(keys)
      _ <- cache.deleteAllByKey(keys)
    } yield ()
  }

}

class ReactiveStorageImpl2[K, V <: Identifiable[K]](storage: Storage2[K,V]) extends ReactiveStorage2[K, V] {

  override val onAdded: SourceStream[Seq[V]] = EventStream()
  override val onUpdated: SourceStream[Seq[(V, V)]] = EventStream()
  override val onDeleted: SourceStream[Set[K]] = EventStream()

  override implicit def ec: ExecutionContext = storage.ec

  override def loadAll(keys: Set[K]): Future[Seq[V]] = storage.loadAll(keys)

  override def saveAll(values: Iterable[V]): Future[Unit] = {
    val valuesByKey = values.toIdMap
    for {
      loadedValues <- loadAll(valuesByKey.keySet)
      loadedValuesByKey = loadedValues.toIdMap
      toSave = Vector.newBuilder[V]
      added = Vector.newBuilder[V]
      updated = Vector.newBuilder[(V, V)]
      _ = valuesByKey.foreach { case (key, next) =>
        val current = loadedValuesByKey.get(key)
        current match {
          case Some(value) if value != next =>
            toSave += next
            updated += value -> next
          case None =>
            toSave += next
            added += next
          case _ => // unchanged, ignore
        }
        next
      }
      _ <- storage.saveAll(toSave.result())
    } yield {
      val addedResult = added.result
      val updatedResult = updated.result
      if (addedResult.nonEmpty) onAdded ! addedResult
      if (updatedResult.nonEmpty) onUpdated ! updatedResult
    }
  }

  override def deleteAllByKey(keys: Set[K]): Future[Unit] = storage.deleteAllByKey(keys).map(_ => onDeleted ! keys)
}

trait CachedStorage[K, V <: Identifiable[K]] {

  //Need to be defs to allow mocking
  def onAdded: EventStream[Seq[V]]
  def onUpdated: EventStream[Seq[(V, V)]]
  def onDeleted: EventStream[Seq[K]]
  def onChanged: EventStream[Seq[V]]

  def blockStreams(block: Boolean): Unit

  protected def load(key: K)(implicit db: DB): Option[V]
  protected def load(keys: Set[K])(implicit db: DB): Seq[V]
  protected def save(values: Seq[V])(implicit db: DB): Unit
  protected def delete(keys: Iterable[K])(implicit db: DB): Unit

  protected def updateInternal(key: K, updater: V => V)(current: V): Future[Option[(V, V)]]

  def find[A, B](predicate: V => Boolean, search: DB => Managed[TraversableOnce[V]], mapping: V => A)(implicit cb: CanBuild[A, B]): Future[B]
  def filterCached(f: V => Boolean): Future[Vector[V]]
  def foreachCached(f: V => Unit): Future[Unit]
  def deleteCached(predicate: V => Boolean): Future[Unit]

  def onChanged(key: K): EventStream[V]
  def onRemoved(key: K): EventStream[K]

  def optSignal(key: K): Signal[Option[V]]
  def signal(key: K): Signal[V]

  def insert(v: V): Future[V]
  def insertAll(vs: Traversable[V]): Future[Set[V]]

  def get(key: K): Future[Option[V]]
  def getOrCreate(key: K, creator: => V): Future[V]
  def list(): Future[Seq[V]]
  def getAll(keys: Traversable[K]): Future[Seq[Option[V]]]

  def update(key: K, updater: V => V): Future[Option[(V, V)]]
  def updateAll(updaters: scala.collection.Map[K, V => V]): Future[Seq[(V, V)]]
  def updateAll2(keys: Iterable[K], updater: V => V): Future[Seq[(V, V)]]

  def updateOrCreate(key: K, updater: V => V, creator: => V): Future[V]

  def updateOrCreateAll(updaters: K Map (Option[V] => V)): Future[Set[V]]
  def updateOrCreateAll2(keys: Iterable[K], updater: ((K, Option[V]) => V)): Future[Set[V]]


  def put(key: K, value: V): Future[V]
  def getRawCached(key: K): Option[V]

  def remove(key: K): Future[Unit]
  def removeAll(keys: Iterable[K]): Future[Unit]

  def cacheIfNotPresent(key: K, value: V): Unit

  def contents: Signal[Map[K, V]]
}

class CachedStorageImpl[K, V <: Identifiable[K]](cache: LruCache[K, Option[V]], db: Database)
                                                (implicit
                                                 val dao: StorageDao[K, V],
                                                 tag: LogTag = LogTag("CachedStorage")
                                                ) extends CachedStorage[K, V] {

  private implicit val dispatcher = new SerialDispatchQueue(name = tag + "_Dispatcher")

  val onAdded = EventStream[Seq[V]]()
  val onUpdated = EventStream[Seq[(V, V)]]()
  val onDeleted = EventStream[Seq[K]]()

  private val onAddedQueue:   BlockingQueue[Seq[V]]     = new LinkedBlockingQueue[Seq[V]]
  private val onUpdatedQueue: BlockingQueue[Seq[(V,V)]] = new LinkedBlockingQueue[Seq[(V,V)]]
  private val onDeletedQueue: BlockingQueue[Seq[K]]     = new LinkedBlockingQueue[Seq[K]]
  private var streamsBlocked = false

  override def blockStreams(block: Boolean): Unit = if (block != streamsBlocked) {
    if (!block) {
      while(!onAddedQueue.isEmpty) onAdded ! onAddedQueue.take()
      while(!onUpdatedQueue.isEmpty) onUpdated ! onUpdatedQueue.take()
      while(!onDeletedQueue.isEmpty) onDeleted ! onDeletedQueue.take()
    }
    streamsBlocked = block
  }

  private def tellAdded(events: Seq[V]): Unit =
    if (!streamsBlocked) onAdded ! events else onAddedQueue.put(events)

  private def tellUpdated(events: Seq[(V,V)]): Unit =
    if (!streamsBlocked) onUpdated ! events else onUpdatedQueue.put(events)

  private def tellDeleted(events: Seq[K]): Unit =
    if (!streamsBlocked) onDeleted ! events else onDeletedQueue.put(events)

  val onChanged = onAdded.union(onUpdated.map(_.map(_._2)))

  protected def load(key: K)(implicit db: DB): Option[V] = dao.getById(key)

  protected def load(keys: Set[K])(implicit db: DB): Seq[V] =
    if (keys.isEmpty) Nil else dao.getAll(keys)

  protected def save(values: Seq[V])(implicit db: DB): Unit =
    if (values.nonEmpty) dao.insertOrReplace(values)

  protected def delete(keys: Iterable[K])(implicit db: DB): Unit =
    if (keys.nonEmpty) dao.deleteEvery(keys)

  private def cachedOrElse(key: K, default: => Future[Option[V]]): Future[Option[V]] =
    Option(cache.get(key)).fold(default)(Future.successful)

  private def loadFromDb(key: K) = db.read { load(key)(_) } map { value =>
    Option(cache.get(key)).getOrElse {
      cache.put(key, value)
      value
    }
  }

  def find[A, B](predicate: V => Boolean, search: DB => Managed[TraversableOnce[V]], mapping: V => A)(implicit cb: CanBuild[A, B]): Future[B] = Future {
    val matches = cb.apply()
    val snapshot = cache.snapshot.asScala

    snapshot.foreach {
      case (k, Some(v)) if predicate(v) => matches += mapping(v)
      case _ =>
    }
    (snapshot.keySet, matches)
  } flatMap { case (wasCached, matches) =>
    db.read { database =>
      val uncached = Map.newBuilder[K, V]
      search(database).acquire { rows =>
        rows.foreach { v =>
          if (! wasCached(v.id)) {
            matches += mapping(v)
            uncached += v.id -> v
          }
        }

        (matches.result, uncached.result)
      }
    }
  } map { case (results, uncached) =>

    uncached.foreach { case (k, v) =>
      if (cache.get(k) eq null) cache.put(k, Some(v))
    }

    results
  }

  def filterCached(f: V => Boolean) = Future { cache.snapshot.values().asScala.filter(_.exists(f)).map(_.get).toVector }

  def foreachCached(f: V => Unit) = Future {
    cache.snapshot.asScala.foreach {
      case (k, Some(v)) => f(v)
      case _ =>
    }
  }

  def deleteCached(predicate: V => Boolean) = Future {
    cache.snapshot.asScala.collect { case (k, Some(v)) if predicate(v) => k } foreach { cache.remove }
  }

  def onChanged(key: K): EventStream[V] = onChanged.map(_.view.filter(_.id == key).lastOption).collect { case Some(v) => v }

  def onRemoved(key: K): EventStream[K] = onDeleted.map(_.view.filter(_ == key).lastOption).collect { case Some(k) => k }

  def optSignal(key: K): Signal[Option[V]] = {
    val changeOrDelete = onChanged(key).map(Option(_)).union(onRemoved(key).map(_ => Option.empty[V]))
    new AggregatingSignal[Option[V], Option[V]](changeOrDelete, get(key), { (_, v) => v })
  }

  def signal(key: K): Signal[V] = optSignal(key).collect { case Some(v) => v }

  def insert(v: V) = put(v.id, v)

  def insertAll(vs: Traversable[V]) =
    updateOrCreateAll(vs.map { v => v.id -> { (_: Option[V]) => v } }(breakOut))

  def get(key: K): Future[Option[V]] = cachedOrElse(key, Future { cachedOrElse(key, loadFromDb(key)) }.flatMap(identity))

  def getOrCreate(key: K, creator: => V): Future[V] = get(key) flatMap { value =>
    value.orElse(Option(cache.get(key)).flatten).fold(addInternal(key, creator))(Future.successful)
  }

  def list() = db.read { dao.list(_) } // TODO: should we update cache?

  def getAll(keys: Traversable[K]): Future[Seq[Option[V]]] = if (keys.isEmpty) Future.successful(Nil) else {
    val cachedEntries = keys.flatMap { key => Option(cache.get(key)) map { value => (key, value) } }.toMap
    val missingKeys = keys.toSet -- cachedEntries.keys

    db.read { db => load(missingKeys)(db) } map { loadedEntries =>
      val loadedMap: Map[K, Option[V]] = loadedEntries.map { value =>
        val key = value.id
        Option(cache.get(key)).map(m => (key, m)).getOrElse {
          cache.put(key, Some(value))
          (key, Some(value))
        }
      }(breakOut)

      keys .map { key =>
        returning(Option(cache.get(key)).orElse(loadedMap.get(key).orElse(cachedEntries.get(key))).getOrElse(None)) { cache.put(key, _) }
      } (breakOut) : Vector[Option[V]]
    }
  }

  def update(key: K, updater: V => V): Future[Option[(V, V)]] = get(key) flatMap { loaded =>
    val prev = Option(cache.get(key)).getOrElse(loaded)
    prev.fold(Future successful Option.empty[(V, V)]) { updateInternal(key, updater)(_) }
  }

  def updateAll(updaters: scala.collection.Map[K, V => V]): Future[Seq[(V, V)]] =
    updateAll2(updaters.keys.toVector, { v => updaters(v.id)(v) })

  def updateAll2(keys: Iterable[K], updater: V => V): Future[Seq[(V, V)]] =
    if (keys.isEmpty) Future successful Seq.empty[(V, V)]
    else
      getAll(keys) flatMap { values =>
        val updated = keys.iterator.zip(values.iterator) .flatMap { case (k, v) =>
          Option(cache.get(k)).flatten.orElse(v).flatMap { value =>
            val updated = updater(value)
            if (updated != value) {
              cache.put(k, Some(updated))
              Some(value -> updated)
            } else None
          }
        } .toVector

        if (updated.isEmpty) Future.successful(Vector.empty)
        else
          db(save(updated.map(_._2))(_)).future.map { _ =>
            tellUpdated(updated)
            updated
          }
      }

  def updateOrCreate(key: K, updater: V => V, creator: => V): Future[V] = get(key) flatMap { loaded =>
    val prev = Option(cache.get(key)).getOrElse(loaded)
    prev.fold { addInternal(key, creator) } { v => updateInternal(key, updater)(v).map(_.fold(v)(_._2)) }
  }

  def updateOrCreateAll(updaters: K Map (Option[V] => V)): Future[Set[V]] =
    updateOrCreateAll2(updaters.keys.toVector, { (key, v) => updaters(key)(v)})

  def updateOrCreateAll2(keys: Iterable[K], updater: ((K, Option[V]) => V)): Future[Set[V]] =
    if (keys.isEmpty) Future successful Set.empty[V]
    else {
      getAll(keys) flatMap { values =>
        val loaded: Map[K, Option[V]] = keys.iterator.zip(values.iterator).map { case (k, v) => k -> Option(cache.get(k)).flatten.orElse(v) }.toMap
        val toSave = Vector.newBuilder[V]
        val added = Vector.newBuilder[V]
        val updated = Vector.newBuilder[(V, V)]

        val result = keys .map { key =>
          val current = loaded.get(key).flatten
          val next = updater(key, current)
          current match {
            case Some(c) if c != next =>
              cache.put(key, Some(next))
              toSave += next
              updated += (c -> next)
            case None =>
              cache.put(key, Some(next))
              toSave += next
              added += next
            case Some(_) => // unchanged, ignore
          }
          next
        } .toSet

        val addedResult = added.result
        val updatedResult = updated.result

        db(save(toSave.result)(_)).future.map { _ =>
          if (addedResult.nonEmpty) tellAdded(addedResult)
          if (updatedResult.nonEmpty) tellUpdated(updatedResult)
          result
        }
      }
    }

  private def addInternal(key: K, value: V): Future[V] = {
    cache.put(key, Some(value))
    db(save(Seq(value))(_)).future.map { _ =>
      tellAdded(Seq(value))
      value
    }
  }

  protected def updateInternal(key: K, updater: V => V)(current: V): Future[Option[(V, V)]] = {
    val updated = updater(current)
    if (updated == current) Future.successful(Some((current, updated)))
    else {
      cache.put(key, Some(updated))
      db(save(Seq(updated))(_)).future.map { _ =>
        tellUpdated(Seq((current, updated)))
        Some((current, updated))
      }
    }
  }

  def put(key: K, value: V): Future[V] = updateOrCreate(key, _ => value, value)

  def getRawCached(key: K): Option[V] = cache.get(key)

  def remove(key: K): Future[Unit] = Future {
    cache.put(key, None)
    db(delete(Seq(key))(_)).future.map { _ =>
      tellDeleted(Seq(key))
    }
  } .flatten

  def removeAll(keys: Iterable[K]): Future[Unit] =
    if (keys.isEmpty) Future.successful(())
    else
      Future {
        keys.foreach { key => cache.put(key, None) }
        db(delete(keys)(_)).future.map { _ => tellDeleted(keys.toVector) }
      } .flatten

  def cacheIfNotPresent(key: K, value: V) = cachedOrElse(key, Future {
    Option(cache.get(key)).getOrElse { returning(Some(value))(cache.put(key, _)) }
  })

  // signal with all data
  override lazy val contents: Signal[Map[K, V]] = {
    val changesStream = EventStream.union[Seq[ContentChange[K, V]]](
      onAdded.map(_.map(d => Added(d.id, d))),
      onUpdated.map(_.map { case (prv, curr) => Updated(prv.id, prv, curr) }),
      onDeleted.map(_.map(Removed(_)))
    )

    def load = for {
      values   <- db.read { dao.list(_) }
      valueMap = values.map { v => v.id -> v }.toMap
    } yield valueMap

    new AggregatingSignal[Seq[ContentChange[K, V]], Map[K, V]](changesStream, load, { (values, changes) =>
      val added = new mutable.HashMap[K, V]
      val removed = new mutable.HashSet[K]
      changes foreach {
        case Added(id, data) =>
          removed -= id
          added += id -> data
        case Updated(id, _, data) =>
          removed -= id
          added += id -> data
        case Removed(id) =>
          removed += id
          added -= id
      }
      values -- removed ++ added
    }).disableAutowiring()
  }
}
