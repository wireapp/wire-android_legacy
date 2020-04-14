package com.waz.db.migration

import com.waz.DisabledTrackingService
import com.waz.db.ZMessagingDB
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.di.StorageModule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers, RobolectricTests}

@RunWith(classOf[JUnitRunner])
class MigrationTest extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {

  protected var zMessagingDB: ZMessagingDB = _

  before {
    zMessagingDB = new ZMessagingDB(Robolectric.application, "test_db", DisabledTrackingService)
  }

  after {
    Robolectric.application.getDatabasePath(zMessagingDB.getDatabaseName).delete()
  }

  protected def closeDB() = zMessagingDB.close()

  protected def withRoomDB(action: UserDatabase => Boolean): Unit = {
    val roomDb =
      StorageModule.getUserDatabase(Robolectric.application, zMessagingDB.getDatabaseName, UserDatabase.getMigrations)
    val result = action(roomDb)
    roomDb.close()
    Robolectric.application.getDatabasePath(zMessagingDB.getDatabaseName).delete()
    assert(result)
  }

}
