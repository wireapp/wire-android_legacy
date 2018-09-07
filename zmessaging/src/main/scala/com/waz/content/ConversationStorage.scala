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

import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog._
import com.waz.api.Verification
import com.waz.api.Verification.UNKNOWN
import com.waz.model.ConversationData.ConversationDataDao
import com.waz.model.ConversationData.ConversationType.Group
import com.waz.model._
import com.waz.service.SearchKey
import com.waz.threading.SerialDispatchQueue
import com.waz.utils._
import com.waz.utils.events._

import scala.collection.immutable.SortedSet
import scala.collection.{GenMap, GenSet, GenTraversableOnce, mutable}
import scala.concurrent.Future

trait ConversationStorage extends CachedStorage[ConvId, ConversationData] {
  def convsSignal: Signal[ConversationsSet]

  def conversations: IndexedSeq[ConversationData]

  def convAdded: EventStream[ConversationData]
  def convDeleted: EventStream[ConversationData]
  def convUpdated: EventStream[(ConversationData, ConversationData)]

  def setUnknownVerification(convId: ConvId): Future[Option[(ConversationData, ConversationData)]]
  def search(prefix: SearchKey, self: UserId, handleOnly: Boolean, teamId: Option[TeamId] = None): Future[Vector[ConversationData]]
  def findByTeams(teams: Set[TeamId]): Future[Set[ConversationData]]
  def getByRemoteIds(remoteId: Traversable[RConvId]): Future[Seq[ConvId]]
  def getByRemoteId(remoteId: RConvId): Future[Option[ConversationData]]
  def getByRemoteIds2(remoteIds: Set[RConvId]): Future[Map[RConvId, ConversationData]]

  def getAllConvs: Future[IndexedSeq[ConversationData]]
  def updateLocalId(oldId: ConvId, newId: ConvId): Future[Option[ConversationData]]
  def updateLocalIds(update: Map[ConvId, ConvId]): Future[Set[ConversationData]]

  def apply[A](f: (GenMap[ConvId, ConversationData], GenMap[RConvId, ConvId]) => A): Future[A]

  def refreshRemoteMap(): Unit
}

class ConversationStorageImpl(storage: ZmsDatabase) extends CachedStorageImpl[ConvId, ConversationData](new UnlimitedLruCache(), storage)(ConversationDataDao, "ConversationStorage_Cached") with ConversationStorage {
  import EventContext.Implicits.global
  private implicit val dispatcher = new SerialDispatchQueue(name = "ConversationStorage")

  private val remoteMap         = new mutable.HashMap[RConvId, ConvId]()
  private val conversationsById = new mutable.HashMap[ConvId, ConversationData]()

  override val convsSignal      = Signal[ConversationsSet]()

  def conversations             = conversationsById.values.toIndexedSeq

  val convAdded = EventStream[ConversationData]()
  val convDeleted = EventStream[ConversationData]()
  val convUpdated = EventStream[(ConversationData, ConversationData)]() // (prev, updated)

  onAdded.on(dispatcher) { cs =>
    verbose(s"${cs.size} convs added")
    cs foreach { c =>
      conversationsById.put(c.id, c)
      remoteMap.put(c.remoteId, c.id)
    }

    convsSignal mutate { _ ++ cs }

    cs foreach (convAdded ! _)

    updateSearchKey(cs)
  }

  def setUnknownVerification(convId: ConvId) = update(convId, { c => c.copy(verified = if (c.verified == Verification.UNVERIFIED) UNKNOWN else c.verified) })

  onDeleted.on(dispatcher) { cs =>
    verbose(s"${cs.size} convs deleted")
    cs foreach { c =>
      conversationsById.remove(c) foreach { cd =>
        if (remoteMap(cd.remoteId) == c) {
          convDeleted ! cd
          remoteMap.remove(cd.remoteId)
        }
      }
    }

    convsSignal mutate { _ -- cs.toSet }
  }

  onUpdated.on(dispatcher) { cs =>
    verbose(s"${cs.size} convs updated")
    cs foreach { case (prev, conv) =>
      conversationsById.put(conv.id, conv)
      if (prev.remoteId != conv.remoteId) {
        remoteMap.remove(prev.remoteId)
        remoteMap.put(conv.remoteId, conv.id)
      }
    }

    convsSignal mutate { _ -- cs.map(_._1.id).toSet ++ cs.map(_._2) }

    cs foreach (convUpdated ! _)

    updateSearchKey(cs collect { case (p, c) if p.name != c.name || (p.convType == Group) != (c.convType == Group) || (c.name.nonEmpty && c.searchKey.isEmpty) => c })
  }

  def refreshRemoteMap(): Unit = {
    remoteMap.clear()
    conversationsById.values.foreach(conv => remoteMap += conv.remoteId -> conv.id)
  }

  private val init = for {
    convs   <- super.list()
    updater = (c: ConversationData) => c.copy(searchKey = c.savedOrFreshSearchKey)
    _       <- updateAll2(convs.map(_.id), updater)
  } yield {
    val updated = convs.map(updater)
    verbose(s"Caching ${updated.size} conversations")
    updated foreach { c =>
      conversationsById(c.id) = c
      remoteMap(c.remoteId) = c.id
    }
    convsSignal ! ConversationsSet(updated.to[SortedSet])
  }

  private def updateSearchKey(cs: Seq[ConversationData]) =
    if (cs.isEmpty) Future successful Nil
    else updateAll2(cs.map(_.id), _.withFreshSearchKey)

  def apply[A](f: (GenMap[ConvId, ConversationData], GenMap[RConvId, ConvId]) => A): Future[A] = init.map(_ => f(conversationsById, remoteMap))

  def getByRemoteId(remoteId: RConvId): Future[Option[ConversationData]] = init.map { _ =>
    remoteMap.get(remoteId).flatMap(conversationsById.get)
  }

  override def getByRemoteIds(remoteId: Traversable[RConvId]): Future[Seq[ConvId]] = init map { _ =>
    remoteId.flatMap(remoteMap.get).toVector
  }

  override def getByRemoteIds2(remoteIds: Set[RConvId]): Future[Map[RConvId, ConversationData]] = init.map { _ =>
    remoteIds.map(rId => rId -> remoteMap.get(rId).flatMap(conversationsById.get)).toMap.collect {
      case (rId, Some(conversationData)) => rId -> conversationData
    }
  }

  override def getAllConvs = init map { _ => conversations }

  override def list: Future[Vector[ConversationData]] = init map { _ => conversationsById.values.toVector }

  def updateLocalId(oldId: ConvId, newId: ConvId) =
    updateLocalIds(Map(oldId -> newId)).map(_.headOption)

  def updateLocalIds(update: Map[ConvId, ConvId]) =
    for {
      _      <- removeAll(update.values)
      convs  <- getAll(update.keys)
      result <- insertAll(convs.flatten.map(c => c.copy(id = update(c.id))))
      _      <- removeAll(update.keys)
    } yield result

  override def search(prefix: SearchKey, self: UserId, handleOnly: Boolean, teamId: Option[TeamId] = None) = storage(ConversationDataDao.search(prefix, self, handleOnly, teamId)(_))

  override def findByTeams(teams: Set[TeamId]) = find(c => c.team.fold(false)(teams.contains), ConversationDataDao.findByTeams(teams)(_), identity)
}

// this wrapper provides a check for content equality instead of the equality based on total ordering provided by the sorted set
case class ConversationsSet(conversations: SortedSet[ConversationData]) {
  def ++(cs: GenTraversableOnce[ConversationData]) = ConversationsSet(conversations ++ cs)
  def --(cs: GenSet[ConvId]) = ConversationsSet(conversations.filter(c => !cs(c.id)))

  override def equals(any: Any) = any match {
    case other @ ConversationsSet(cs) => this.eq(other) || cs.iterator.corresponds(conversations)(_ == _)
    case _ => false
  }
}
