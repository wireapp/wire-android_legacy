package com.waz.db.migration

import com.waz.db.ZMessagingDB
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.di.StorageModule
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers, RobolectricTests}

@RunWith(classOf[JUnitRunner])
@Config(sdk=Array(21))
class MigrationTest extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {

  protected var zMessagingDB: ZMessagingDB = _

  before {
    zMessagingDB = new ZMessagingDB(RuntimeEnvironment.application, "test_db")
  }

  after {
    RuntimeEnvironment.application.getDatabasePath(zMessagingDB.getDatabaseName).delete()
  }

  protected def closeDB() = zMessagingDB.close()

  protected def withRoomDB(action: UserDatabase => Boolean): Unit = {
    val roomDb =
      StorageModule.getUserDatabase(RuntimeEnvironment.application, zMessagingDB.getDatabaseName, UserDatabase.getMigrations)
    val result = action(roomDb)
    roomDb.close()
    RuntimeEnvironment.application.getDatabasePath(zMessagingDB.getDatabaseName).delete()
    assert(result)
  }

}
