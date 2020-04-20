package com.waz.db.migration.globaldatabase

import com.waz.DisabledTrackingService
import com.waz.db.ZGlobalDB
import com.waz.zclient.storage.db.GlobalDatabase
import com.waz.zclient.storage.di.StorageModule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers, RobolectricTests}

@RunWith(classOf[JUnitRunner])
class GlobalDatabaseMigrationTest extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {

  protected var zGlobalDb: ZGlobalDB = _
  private lazy val dbName = zGlobalDb.getDatabaseName

  before {
    zGlobalDb = new ZGlobalDB(Robolectric.application, tracking = DisabledTrackingService)
  }

  after {
    Robolectric.application.getDatabasePath(dbName).delete()
  }

  protected def closeDB(): Unit = zGlobalDb.close()

  protected def withRoomDB(action: GlobalDatabase => Boolean): Unit = {
    val roomDb =
      StorageModule.getGlobalDatabase(Robolectric.application, GlobalDatabase.getMigrations)
    val result = action(roomDb)
    roomDb.close()
    Robolectric.application.getDatabasePath(dbName).delete()
    assert(result)
  }

}
