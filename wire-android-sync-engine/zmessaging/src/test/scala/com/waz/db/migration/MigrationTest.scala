package com.waz.db.migration

import com.waz.DisabledTrackingService
import com.waz.db.ZMessagingDB
import com.waz.model.RemoteInstant
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.di.StorageModule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers, RobolectricTests}

import scala.concurrent.duration.FiniteDuration

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

  protected def validateMigrationAndClose(): Unit = {
    withRoomDB({ _ => true}) //no read/write validation. just let the migration happen./no read/write validation. just let the migration happen.
  }

  protected def convertRemoteInstant(remoteInstant: RemoteInstant): Int =
    java.lang.Long.valueOf(remoteInstant.toEpochMilli).toInt

  protected def convertFiniteDuration(finiteDuration: Option[FiniteDuration]): Int =
    finiteDuration.fold(null.asInstanceOf[Int]) { d => java.lang.Long.valueOf(d.toMillis).toInt }

}
