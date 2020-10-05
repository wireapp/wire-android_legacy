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
import com.waz.model.Liking.{Action, LikingDao}
import com.waz.model._
import com.wire.signals.CancellableFuture
import com.waz.utils.TrimmingLruCache.Fixed
import com.wire.signals.{AggregatingSignal, RefreshingSignal, Signal}
import com.waz.utils._

import scala.collection.{breakOut, mutable}
import scala.concurrent.duration._
import scala.concurrent.Future

trait ReactionsStorage extends CachedStorage[(MessageId, UserId), Liking] {
  def loadAll(msgs: Seq[MessageId]): Future[Vector[Likes]]
  def addOrUpdate(liking: Liking): Future[Likes]
  def getLikes(msg: MessageId): Future[Likes]
  def likes(msg:MessageId): Signal[Likes]
}

class ReactionsStorageImpl(context: Context, storage: Database)
  extends CachedStorageImpl[(MessageId, UserId), Liking](new TrimmingLruCache(context, Fixed(MessagesStorage.cacheSize)), storage)(LikingDao, LogTag("LikingStorage"))
    with ReactionsStorage
    with DerivedLogTag {

  import ReactionsStorageImpl._

  import com.waz.threading.Threading.Implicits.Background

  private val likesCache = new TrimmingLruCache[MessageId, Map[UserId, RemoteInstant]](context, Fixed(1024))
  private val maxTime = returning(new AggregatingSignal[RemoteInstant, RemoteInstant](onChanged.map(_.maxBy(_.timestamp).timestamp), storage.read(LikingDao.findMaxTime(_)), _ max _))(_.disableAutowiring())

  onChanged.on(Background) { likes =>
    likes.groupBy(_.message) foreach { case (msg, ls) =>
      Option(likesCache.get(msg)) foreach { current =>
        val (toAdd, toRemove) = ls.partition(_.action == Action.Like)
        likesCache.put(msg, current -- toRemove.map(_.user) ++ toAdd.map(l => l.user -> l.timestamp))
      }
    }
  }

  private def updateCache(msg: MessageId, likings: Iterable[Liking]) = {
    val users: Map[UserId, RemoteInstant] = likings.collect { case l if l.action == Action.Like => l.user -> l.timestamp } (breakOut)
    likesCache.put(msg, users)
    Likes(msg, users)
  }

  override def insertAll(vs: Traversable[Liking]): Future[Set[Liking]] = {
    val values = new mutable.HashMap[(MessageId, UserId), Liking]
    vs foreach { v =>
      if (values.get(v.id).forall(_.timestamp.isBefore(v.timestamp)))
        values(v.id) = v
    }
    updateOrCreateAll2(values.keys, { (id, v) => v.fold(values(id)) { _ max values(id) }})
  }

  override def getLikes(msg: MessageId): Future[Likes] = Future {
    Option(likesCache.get(msg))
  } flatMap {
    case Some(users) => Future.successful(Likes(msg, users))
    case None => find(_.message == msg, LikingDao.findForMessage(msg)(_), identity) map { updateCache(msg, _) }
  }

  override def likes(msg: MessageId): Signal[Likes] =
    new RefreshingSignal[Likes](
      CancellableFuture.lift(getLikes(msg)),
      onChanged.map(_.filter(_.message == msg))
    )

  override def addOrUpdate(liking: Liking): Future[Likes] = {
    if (liking.timestamp.isEpoch) updateOrCreate(liking.id, l => l.copy(timestamp = maxTime.currentValue.getOrElse(l.timestamp) + 1.milli, action = liking.action), liking) // local update
    else updateOrCreate(liking.id, _ max liking, liking)
  }.flatMap(_ => getLikes(liking.message))

  override def loadAll(msgs: Seq[MessageId]): Future[Vector[Likes]] = Future {
    msgs.map(m => m -> Option(likesCache.get(m))).toMap
  } flatMap { cached =>
    val toLoad: Set[MessageId] = cached.collect { case (id, None) => id } (breakOut)
    find(l => toLoad(l.message), LikingDao.findForMessages(toLoad)(_), identity) map { likings =>
      val usersMap = cached.mapValues(_.getOrElse(Map.empty)) ++ likings.groupBy(_.message).map { case (msg, ls) => msg -> likers(ls) }
      msgs.map { msg =>
        val users = usersMap(msg)
        if (likesCache.get(msg) == null) likesCache.put(msg, users)
        Likes(msg, users)
      } (breakOut)
    }
  }
}

object ReactionsStorageImpl {

  private def likers(likings: Seq[Liking]): Map[UserId, RemoteInstant] =
    likings.collect { case l if l.action == Action.Like => l.user -> l.timestamp } (breakOut)
}

case class Likes(message: MessageId, likers: Map[UserId, RemoteInstant])

object Likes {
  def Empty(message: MessageId) = Likes(message, Map())
}
