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

import android.content.Context
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationMemberData.ConversationMemberDataDao
import com.waz.model._
import com.waz.utils.TrimmingLruCache.Fixed
import com.wire.signals.{AggregatingSignal, Signal}
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}

import scala.concurrent.Future

trait MembersStorage extends CachedStorage[(UserId, ConvId), ConversationMemberData] {
  def getByConv(conv: ConvId): Future[IndexedSeq[ConversationMemberData]]
  def getByConvs(conv: Set[ConvId]): Future[IndexedSeq[ConversationMemberData]]
  def updateOrCreateAll(conv: ConvId, users: Map[UserId, ConversationRole]): Future[Set[ConversationMemberData]]
  def updateOrCreateAll(conv: ConvId, user: UserId, role: ConversationRole): Future[Option[ConversationMemberData]]
  def isActiveMember(conv: ConvId, user: UserId): Future[Boolean]
  def remove(conv: ConvId, users: Iterable[UserId]): Future[Set[ConversationMemberData]]
  def remove(conv: ConvId, user: UserId): Future[Option[ConversationMemberData]]
  def getByUsers(users: Set[UserId]): Future[IndexedSeq[ConversationMemberData]]
  def getActiveUsers(conv: ConvId): Future[Seq[UserId]]
  def getActiveUsers2(conv: Set[ConvId]): Future[Map[ConvId, Set[UserId]]]
  def getActiveConvs(user: UserId): Future[Seq[ConvId]]
  def activeMembers(conv: ConvId): Signal[Set[UserId]]
  def set(conv: ConvId, users: Map[UserId, ConversationRole]): Future[Unit]
  def setAll(members: Map[ConvId, Map[UserId, ConversationRole]]): Future[Unit]
  def addAll(members: Map[ConvId, Map[UserId, ConversationRole]]): Future[Unit]
  def delete(conv: ConvId): Future[Unit]
  def updateOrCreate(conv: ConvId, user: UserId, role: ConversationRole): Future[Unit]
}

class MembersStorageImpl(context: Context, storage: ZmsDatabase)
  extends CachedStorageImpl[(UserId, ConvId), ConversationMemberData](new TrimmingLruCache(context, Fixed(1024)), storage)(ConversationMemberDataDao, LogTag("MembersStorage_Cached"))
    with MembersStorage with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  def getByConv(conv: ConvId) = find(_.convId == conv, ConversationMemberDataDao.findForConv(conv)(_), identity)

  def getByUser(user: UserId) = find(_.userId == user, ConversationMemberDataDao.findForUser(user)(_), identity)

  override def activeMembers(conv: ConvId): Signal[Set[UserId]] = {
    def onConvMemberChanged(conv: ConvId) =
      onAdded.map(_.filter(_.convId == conv).map(_.userId -> true))
        .zip(onDeleted.map(_.filter(_._2 == conv).map(_._1 -> false)))

    new AggregatingSignal[Seq[(UserId, Boolean)], Set[UserId]](
      onConvMemberChanged(conv),
      getActiveUsers(conv).map(_.toSet),
      { (current, changes) =>
        val (active, inactive) = changes.partition(_._2)
        current -- inactive.map(_._1) ++ active.map(_._1)
      })
  }

  override def getActiveUsers(conv: ConvId) = getByConv(conv) map { _.map(_.userId) }

  override def getActiveConvs(user: UserId) = getByUser(user) map { _.map(_.convId) }

  override def getActiveUsers2(convs: Set[ConvId]): Future[Map[ConvId, Set[UserId]]] =
    getByConvs(convs).map(_.groupBy(_.convId).map {
      case (cId, members) => cId -> members.map(_.userId).toSet
    })

  override def updateOrCreateAll(conv: ConvId, users: Map[UserId, ConversationRole]): Future[Set[ConversationMemberData]] =
    updateOrCreateAll2(users.keys.map((_, conv)), { (k, v) =>
      v match {
        case Some(m) if m.role != users(m.userId).label => m.copy(role = users(m.userId).label)
        case Some(m)                                    => m
        case None                                       => ConversationMemberData(k._1, conv, users(k._1))
      }
    })

  override def updateOrCreateAll(conv: ConvId, user: UserId, role: ConversationRole): Future[Option[ConversationMemberData]] =
    updateOrCreateAll(conv, Map(user -> role)).map(_.headOption)

  override def remove(conv: ConvId, users: Iterable[UserId]): Future[Set[ConversationMemberData]] =
    getAll(users.map(_ -> conv)).flatMap(toBeRemoved => removeAll(users.map(_ -> conv)).map(_ => toBeRemoved.flatten.toSet))

  override def remove(conv: ConvId, user: UserId): Future[Option[ConversationMemberData]] =
    remove(conv, Set(user)).map(_.headOption)

  override def set(conv: ConvId, users: Map[UserId, ConversationRole]): Future[Unit] = getActiveUsers(conv).flatMap { active =>
    val toRemove = active.filterNot(users.keySet)
    val toAdd = users -- toRemove

    remove(conv, toRemove).zip(updateOrCreateAll(conv, toAdd)).map(_ => ())
  }

  override def setAll(members: Map[ConvId, Map[UserId, ConversationRole]]): Future[Unit] = getActiveUsers2(members.keySet).flatMap { active =>
    val toRemove = active.map {
      case (convId, users) => convId -> active.get(convId).map(_.filterNot(users)).getOrElse(Set())
    }

    val toAdd = members.map {
      case (convId, users) => convId -> (users -- toRemove.getOrElse(convId, Set()))
    }

    val removeList = toRemove.toSeq.flatMap {
      case (convId, users) => users.map((_, convId))
    }

    val addList = toAdd.flatMap {
      case (convId, users) => users.map(m => ConversationMemberData(m._1, convId, m._2))
    }

    removeAll(removeList).zip(insertAll(addList)).map(_ => ())
  }

  override def addAll(members: Map[ConvId, Map[UserId, ConversationRole]]): Future[Unit] = {
    val addList =
      members.flatMap { case (convId, users) => users.map(u => ConversationMemberData(u._1, convId, u._2)) }

    insertAll(addList).map(_ => ())
  }

  override def updateOrCreate(conv: ConvId, user: UserId, role: ConversationRole): Future[Unit] =
    updateOrCreate((user, conv), _.copy(role = role.label), ConversationMemberData(user, conv, role)).map(_ => ())

  override def isActiveMember(conv: ConvId, user: UserId): Future[Boolean] = get(user -> conv).map(_.nonEmpty)

  override def delete(conv: ConvId): Future[Unit] = getByConv(conv) flatMap { users => removeAll(users.map(_.userId -> conv)) }

  override def getByUsers(users: Set[UserId]): Future[IndexedSeq[ConversationMemberData]] =
    find(mem => users.contains(mem.userId), ConversationMemberDataDao.findForUsers(users)(_), identity)

  override def getByConvs(convs: Set[ConvId]): Future[IndexedSeq[ConversationMemberData]] =
    find(mem => convs.contains(mem.convId), ConversationMemberDataDao.findForConvs(convs)(_), identity)
}
