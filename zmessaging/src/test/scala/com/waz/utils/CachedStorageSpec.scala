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

import com.waz.ZIntegrationMockSpec
import com.waz.utils.StorageTestData._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CachedStorageSpec extends ZIntegrationMockSpec {

  private val main  = mock[Storage2[Int, TestObject]]
  private val cache = mock[Storage2[Int, TestObject]]

  feature("CachedStorage specific features") {

    scenario("Load, when values exist in cache, return that values and do not touch main storage") {
      val keys = values.map(keyExtractor)

      (cache.keyExtractor _).expects().anyNumberOfTimes().returns(keyExtractor)
      (main.keyExtractor _).expects().anyNumberOfTimes().returns(keyExtractor)

      (cache.loadAll _)
        .expects(where { keys: Set[Int] => keys.size == values.size })
        .once()
        .returning(Future.successful(values.toSeq))
      (main.loadAll _).expects(*).never()

      val cachedStorage = new CachedStorage2(main = main, cache = cache)
      for {
        loaded <- cachedStorage.loadAll(keys)
      } yield {
        loaded.map(keyExtractor).toSet shouldBe keys
      }
    }

    scenario("Load, when values do not exist in cache, return that values from the main storage and put them in cache") {
      val keys = values.map(keyExtractor)

      (cache.keyExtractor _).expects().anyNumberOfTimes().returns(keyExtractor)
      (main.keyExtractor _).expects().anyNumberOfTimes().returns(keyExtractor)

      (cache.loadAll _)
        .expects(where { keys: Set[Int] => keys.size == values.size })
        .once()
        .returning(Future.successful(Seq.empty))
      (main.loadAll _)
        .expects(where { keys: Set[Int] => keys.size == values.size })
        .once()
        .returning(Future.successful(values.toSeq))

      (cache.saveAll _)
        .expects(where { xs: Iterable[TestObject] => xs.size == values.size })
        .once()
        .returning(Future.successful(()))

      val cachedStorage = new CachedStorage2(main = main, cache = cache)
      for {
        loaded <- cachedStorage.loadAll(keys)
      } yield {
        loaded.map(keyExtractor).toSet shouldBe keys
      }
    }

    scenario("Should remove all values from cache and from main on delete") {
      val keys = values.map(keyExtractor)

      (cache.keyExtractor _).expects().anyNumberOfTimes().returns(keyExtractor)
      (main.keyExtractor _).expects().anyNumberOfTimes().returns(keyExtractor)

      (cache.deleteAllByKey _)
        .expects(where { keys: Set[Int] => keys.size == values.size })
        .once()
        .returning(Future.successful(()))
      (main.deleteAllByKey _)
        .expects(where { keys: Set[Int] => keys.size == values.size })
        .once()
        .returning(Future.successful(()))

      val cachedStorage = new CachedStorage2(main = main, cache = cache)
      for {
        _ <- cachedStorage.deleteAllByKey(keys)
      } yield { succeed }
    }

    scenario("Should save all values in cache and in main on save") {
      (cache.keyExtractor _).expects().anyNumberOfTimes().returns(keyExtractor)
      (main.keyExtractor _).expects().anyNumberOfTimes().returns(keyExtractor)

      (cache.saveAll _)
        .expects(where { xs: Iterable[TestObject] => xs.size == values.size })
        .once()
        .returning(Future.successful(()))
      (main.saveAll _)
        .expects(where { xs: Iterable[TestObject] => xs.size == values.size })
        .once()
        .returning(Future.successful(()))

      val cachedStorage = new CachedStorage2(main = main, cache = cache)
      for {
        _ <- cachedStorage.saveAll(values)
      } yield { succeed }
    }

  }

}
