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
import com.waz.model.ConversationMemberData.ConversationMemberDataDao
import com.waz.model._
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.events.{AggregatingSignal, Signal}
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}

import scala.concurrent.Future

trait MembersStorage extends CachedStorage[(UserId, ConvId), ConversationMemberData] {
  def getByConv(conv: ConvId): Future[IndexedSeq[ConversationMemberData]]
  def getByConvs(conv: Set[ConvId]): Future[IndexedSeq[ConversationMemberData]]
  def add(conv: ConvId, users: Iterable[UserId]): Future[Set[ConversationMemberData]]
  def add(conv: ConvId, user: UserId): Future[Option[ConversationMemberData]]
  def isActiveMember(conv: ConvId, user: UserId): Future[Boolean]
  def remove(conv: ConvId, users: Iterable[UserId]): Future[Set[ConversationMemberData]]
  def remove(conv: ConvId, user: UserId): Future[Option[ConversationMemberData]]
  def getByUsers(users: Set[UserId]): Future[IndexedSeq[ConversationMemberData]]
  def getActiveUsers(conv: ConvId): Future[Seq[UserId]]
  def getActiveConvs(user: UserId): Future[Seq[ConvId]]
  def activeMembers(conv: ConvId): Signal[Set[UserId]]
  def set(conv: ConvId, users: Set[UserId]): Future[Unit]
  def delete(conv: ConvId): Future[Unit]
}

class MembersStorageImpl(context: Context, storage: ZmsDatabase) extends CachedStorageImpl[(UserId, ConvId), ConversationMemberData](new TrimmingLruCache(context, Fixed(1024)), storage)(ConversationMemberDataDao, "MembersStorage_Cached") with MembersStorage {
  private implicit val dispatcher = new SerialDispatchQueue(name = "MembersStorage")

  def getByConv(conv: ConvId) = find(_.convId == conv, ConversationMemberDataDao.findForConv(conv)(_), identity)

  def getByUser(user: UserId) = find(_.userId == user, ConversationMemberDataDao.findForUser(user)(_), identity)

  def activeMembers(conv: ConvId): Signal[Set[UserId]] =
    new AggregatingSignal[Seq[(UserId, Boolean)], Set[UserId]](onConvMemberChanged(conv),
                                                                getActiveUsers(conv).map(_.toSet), { (current, changes) =>
    val (active, inactive) = changes.partition(_._2)

    current -- inactive.map(_._1) ++ active.map(_._1)
  })

  private def onConvMemberChanged(conv: ConvId) = onAdded.map(_.filter(_.convId == conv).map(_.userId -> true)).union(onDeleted.map(_.filter(_._2 == conv).map(_._1 -> false)))

  override def getActiveUsers(conv: ConvId) = getByConv(conv) map { _.map(_.userId) }

  override def getActiveConvs(user: UserId) = getByUser(user) map { _.map(_.convId) }

  def add(conv: ConvId, users: Iterable[UserId]) =
    updateOrCreateAll2(users.map((_, conv)), { (k, v) =>
      v match {
        case Some(m) => m
        case None    => ConversationMemberData(k._1, conv)
      }
    })

  def add(conv: ConvId, user: UserId) =
    add(conv, Set(user)).map(_.headOption)

  override def remove(conv: ConvId, users: Iterable[UserId]) = {
    getAll(users.map(_ -> conv)).flatMap(toBeRemoved => removeAll(users.map(_ -> conv)).map(_ => toBeRemoved.flatten.toSet))
  }

  override def remove(conv: ConvId, user: UserId) =
    remove(conv, Set(user)).map(_.headOption)

  def set(conv: ConvId, users: Set[UserId]): Future[Unit] = getActiveUsers(conv) flatMap { active =>
    val toRemove = active.filterNot(users)
    val toAdd = users -- toRemove

    remove(conv, toRemove).zip(add(conv, toAdd)).map(_ => ())
  }

  override def isActiveMember(conv: ConvId, user: UserId) = get(user -> conv).map(_.nonEmpty)

  def delete(conv: ConvId) = getByConv(conv) flatMap { users => removeAll(users.map(_.userId -> conv)) }

  override def getByUsers(users: Set[UserId]) = find(mem => users.contains(mem.userId), ConversationMemberDataDao.findForUsers(users)(_), identity)

  override def getByConvs(convs: Set[ConvId]) = find(mem => convs.contains(mem.convId), ConversationMemberDataDao.findForConvs(convs)(_), identity)
}
