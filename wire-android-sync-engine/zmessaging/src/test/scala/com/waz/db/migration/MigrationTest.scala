package com.waz.db.migration

import com.waz.{DisabledTrackingService, KotlinMigrationHelper}
import com.waz.db.ZMessagingDB
import com.waz.model.KeyValueData
import com.waz.model.KeyValueData.KeyValueDataDao
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.property.KeyValuesEntity
import com.waz.zclient.storage.di.StorageModule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers, RobolectricTests}

@RunWith(classOf[JUnitRunner])
class MigrationTest extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {

  var zMessagingDB: ZMessagingDB = _

  before {
    zMessagingDB = new ZMessagingDB(Robolectric.application, "test_db", DisabledTrackingService)
  }

  after {
    Robolectric.application.getDatabasePath(zMessagingDB.getDatabaseName).delete()
  }

  def closeDB() = zMessagingDB.close()

  def withRoomDB(action: UserDatabase => Unit): Unit = {
    val roomDb =
      StorageModule.getUserDatabase(Robolectric.application, zMessagingDB.getDatabaseName, UserDatabase.getMigrations)
    action(roomDb)
    roomDb.close()
    Robolectric.application.getDatabasePath(zMessagingDB.getDatabaseName).delete()
  }

  scenario("KeyValue migration") {
    implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
    KeyValueDataDao.insertOrReplace(KeyValueData("key", "value"))
    closeDB()
    withRoomDB({ KotlinMigrationHelper.assertKeyValue(_, new KeyValuesEntity("key","value"))})
  }
}
