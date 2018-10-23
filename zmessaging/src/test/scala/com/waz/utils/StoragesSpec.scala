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

import android.support.v4.util.LruCache
import com.waz.ZIntegrationSpec
import com.waz.utils.StorageTestData._

import scala.concurrent.ExecutionContext.Implicits.global

abstract class StorageSpec(storageCreator: () => Storage2[Int, TestObject]) extends ZIntegrationSpec {
  lazy val storageClassName: String = storageCreator().getClass.getName

  var storage: Storage2[Int, TestObject] = _

  override protected def beforeEach(): Unit = {
    storage = storageCreator()
  }

  feature(s"Basic storage features of '$storageClassName'") {

    scenario("Save and load values without preserving the order") {
      for {
        _ <- storage.saveAll(values)
        keys = values.map(keyExtractor)
        loaded <- storage.loadAll(keys)
      } yield loaded.map(keyExtractor).toSet shouldBe keys
    }

    scenario("Save and load one value") {
      val value = values.head
      for {
        _ <- storage.save(value)
        loaded <- storage.find(keyExtractor(value))
      } yield loaded shouldBe Some(value)
    }

    scenario("In case if value is not in storage, return None") {
      for {
        loaded <- storage.find(1)
      } yield loaded shouldBe None
    }

    scenario("In case if not all requested values are not in storage, return values that are in storage") {
      for {
        _ <- storage.saveAll(values.tail)
        loaded <- storage.loadAll(values.map(keyExtractor))
      } yield loaded.size shouldBe values.size - 1
    }

    scenario("Delete values by keys") {
      for {
        _ <- storage.saveAll(values)
        keys = values.map(keyExtractor)
        _ <- storage.deleteAllByKey(keys)
        loaded <- storage.loadAll(keys)
      } yield loaded.size shouldBe 0
    }

    scenario("Delete one value by key") {
      val value = values.head
      for {
        _ <- storage.save(value)
        _ <- storage.deleteByKey(keyExtractor(value))
        loaded <- storage.find(keyExtractor(value))
      } yield loaded shouldBe None
    }

    scenario("Delete values") {
      for {
        _ <- storage.saveAll(values)
        _ <- storage.deleteAll(values)
        keys = values.map(keyExtractor)
        loaded <- storage.loadAll(keys)
      } yield loaded.size shouldBe 0
    }

    scenario("Delete one value") {
      val value = values.head
      for {
        _ <- storage.save(value)
        _ <- storage.delete(value)
        loaded <- storage.find(keyExtractor(value))
      } yield loaded shouldBe None
    }

    scenario("Update value if it is in the storage and return previous and current version of the value") {
      val oldValue = values.head
      for {
        _ <- storage.save(oldValue)
        key = keyExtractor(oldValue)
        changedValue = oldValue.copy(title = "changed title")
        Some((previous, current)) <- storage.update(key, _ => changedValue)
        Some(currentFromStorage) <- storage.find(key)
      } yield {
        currentFromStorage shouldBe current
        (oldValue, changedValue) shouldBe (previous, current)
      }
    }

    scenario("Do not update anything and return None if value is not in the storage") {
      val notStoredValue = values.head
      for {
        updatingResult <- storage.update(keyExtractor(notStoredValue), _.copy(title = "changed title"))
        loadedFromStorage <- storage.find(keyExtractor(notStoredValue))
      } yield {
        loadedFromStorage shouldBe None
        updatingResult shouldBe None
      }
    }

  }

}

class InMemoryStorageSpec extends StorageSpec(() => new InMemoryStorage2(new LruCache(1000), keyExtractor))
class CachedStorageBasicSpec extends StorageSpec(() => new CachedStorage2(new UnlimitedInMemoryStorage(keyExtractor), new UnlimitedInMemoryStorage(keyExtractor)))
class UnlimitedInMemoryStorageSpec extends StorageSpec(() => new UnlimitedInMemoryStorage(keyExtractor))
