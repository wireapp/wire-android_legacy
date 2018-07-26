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

import java.util.concurrent
import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.{ExecutionContext, Future}

class UnlimitedInMemoryStorage[K,V](override val keyExtractor: V => K)
                                   (implicit
                                    override val ec: ExecutionContext) extends Storage2[K, V] {

  private val map: ConcurrentHashMap[K,V] = new concurrent.ConcurrentHashMap[K, V]()

  override def loadAll(keys: Set[K]): Future[Seq[V]] = Future(keys.toSeq.flatMap(k => Option(map.get(k))))
  override def saveAll(values: Iterable[V]): Future[Unit] = Future(values.map(v => map.put(keyExtractor(v), v)))
  override def deleteAllByKey(keys: Set[K]): Future[Unit] = Future(keys.map(map.remove))
}
