/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.storage

import android.content.Context
import android.support.test.InstrumentationRegistry
import com.waz.db.{Dao, DaoDB}
import com.waz.service.tracking.DummyTrackingService
import com.waz.utils.{DbStorage2, Identifiable}
import com.waz.utils.wrappers.DB
import com.waz.zclient.TestUtils.asyncTest
import org.junit.{After, Before, Test}

import scala.concurrent.ExecutionContext.Implicits.global

object GeneralStorageTest {

  class TestSingleDaoDb(context: Context, databaseName: String, dao: Dao[_, _])
    extends DaoDB(context, databaseName, null, 1, Seq(dao), migrations = Seq.empty, new DummyTrackingService())

}

abstract class GeneralStorageTest[Id, Entity <: Identifiable[Id]](dao: Dao[Entity, Id])(entities: Set[Entity]) {
  import com.waz.zclient.storage.GeneralStorageTest._

  val DatabaseName = "test_db"

  var testDB: DB = _
  var storage: DbStorage2[Id, Entity] = _

  @Before
  def initializeDB(): Unit = {
    val context = InstrumentationRegistry.getTargetContext
    testDB = new TestSingleDaoDb(context, DatabaseName, dao).getWritableDatabase
    storage = new DbStorage2[Id, Entity](dao)(global, testDB)
  }

  @After
  def removeDb(): Unit = {
    val context = InstrumentationRegistry.getTargetContext
    testDB.close()
    context.deleteDatabase(DatabaseName)
  }

  @Test
  def testWriteRead(): Unit = asyncTest {
    for {
      _ <- storage.saveAll(entities)
      ids = entities.map(_.id)
      res <- storage.loadAll(ids)
    } yield {
      val loaded = res.toSet
      assert(loaded == entities, s"\nLoaded: $loaded\nExpected: $entities")
    }
  }

}
