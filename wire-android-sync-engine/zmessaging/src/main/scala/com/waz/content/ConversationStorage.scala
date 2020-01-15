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

import com.waz.log.LogSE._
import com.waz.api.Verification
import com.waz.api.Verification.UNKNOWN
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationDataDao
import com.waz.model.ConversationData.ConversationType.Group
import com.waz.model._
import com.waz.service.SearchKey
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.Locales.currentLocaleOrdering
import com.waz.utils._
import com.waz.utils.events._

import scala.collection.GenMap
import scala.concurrent.Future

trait ConversationStorage extends CachedStorage[ConvId, ConversationData] {
  def setUnknownVerification(convId: ConvId): Future[Option[(ConversationData, ConversationData)]]
  def getByRemoteIds(remoteId: Traversable[RConvId]): Future[Seq[ConvId]]
  def getByRemoteId(remoteId: RConvId): Future[Option[ConversationData]]
  def getByRemoteIds2(remoteIds: Set[RConvId]): Future[Map[RConvId, ConversationData]]

  def updateLocalId(oldId: ConvId, newId: ConvId): Future[Option[ConversationData]]
  def updateLocalIds(update: Map[ConvId, ConvId]): Future[Set[ConversationData]]

  def apply[A](f: GenMap[ConvId, ConversationData] => A): Future[A]

  def findGroupConversations(prefix: SearchKey, self: UserId, limit: Int, handleOnly: Boolean): Future[Seq[ConversationData]]
}

class ConversationStorageImpl(storage: ZmsDatabase)
  extends CachedStorageImpl[ConvId, ConversationData](new UnlimitedLruCache(), storage)(ConversationDataDao, LogTag("ConversationStorage_Cached"))
    with ConversationStorage with DerivedLogTag {

  import EventContext.Implicits.global
  private implicit val dispatcher = new SerialDispatchQueue(name = "ConversationStorage")

  onAdded.on(dispatcher) { cs => updateSearchKey(cs)}

  def setUnknownVerification(convId: ConvId) =
    update(convId, { c => c.copy(verified = if (c.verified == Verification.UNVERIFIED) UNKNOWN else c.verified) })

  onUpdated.on(dispatcher) { cs =>
    updateSearchKey(cs.collect {
      case (p, c) if p.name != c.name || (p.convType == Group) != (c.convType == Group) || (c.name.nonEmpty && c.searchKey.isEmpty) => c
    })
  }

  private val init = for {
    convs   <- super.list()
    updater = (c: ConversationData) => c.copy(searchKey = c.savedOrFreshSearchKey)
    _       <- updateAll2(convs.map(_.id), updater)
  } yield {
    verbose(l"Caching ${convs.size} conversations")
  }

  private def updateSearchKey(cs: Seq[ConversationData]) =
    if (cs.isEmpty) Future successful Nil
    else updateAll2(cs.map(_.id), _.withFreshSearchKey)

  def apply[A](f: GenMap[ConvId, ConversationData] => A): Future[A] = init.flatMap(_ => contents.head).map(f)

  def getByRemoteId(remoteId: RConvId): Future[Option[ConversationData]] = init.flatMap { _ =>
    findByRemoteId(remoteId).map(_.headOption)
  }

  override def getByRemoteIds(remoteIds: Traversable[RConvId]): Future[Seq[ConvId]] =
    getByRemoteIds2(remoteIds.toSet).map { convs =>
      remoteIds.flatMap(rId => convs.get(rId).map(_.id)).toSeq
    }

  override def getByRemoteIds2(remoteIds: Set[RConvId]): Future[Map[RConvId, ConversationData]] = init.flatMap { _ =>
    findByRemoteIds(remoteIds).map { convs =>
      remoteIds.flatMap(rId => convs.find(_.remoteId == rId)).map(c => c.remoteId -> c).toMap
    }
  }

  override def list: Future[Vector[ConversationData]] = init flatMap { _ => contents.head.map(_.values.toVector)  }

  def updateLocalId(oldId: ConvId, newId: ConvId) =
    updateLocalIds(Map(oldId -> newId)).map(_.headOption)

  def updateLocalIds(update: Map[ConvId, ConvId]) =
    for {
      _      <- removeAll(update.values)
      convs  <- getAll(update.keys)
      result <- insertAll(convs.flatten.map(c => c.copy(id = update(c.id))))
      _      <- removeAll(update.keys)
    } yield result

  override def findGroupConversations(prefix: SearchKey, self: UserId, limit: Int, handleOnly: Boolean): Future[Seq[ConversationData]] =
    storage(ConversationDataDao.search(prefix, self, handleOnly, None)(_)).map(_.sortBy(_.displayName.str)(currentLocaleOrdering).take(limit))

  private def findByRemoteId(remoteId: RConvId) = find(c => c.remoteId == remoteId, ConversationDataDao.findByRemoteId(remoteId)(_), identity)
  private def findByRemoteIds(remoteIds: Set[RConvId]) = find(c => remoteIds.contains(c.remoteId), ConversationDataDao.findByRemoteIds(remoteIds)(_), identity)
}

