package com.waz.db.migration.userdatabase

import com.waz.DisabledTrackingService
import com.waz.content.ZmsDatabase
import com.waz.db.{BaseDaoDB, ZMessagingDB}
import com.waz.model.KeyValueData.KeyValueDataDao
import com.waz.model.{KeyValueData, UserId}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers, RobolectricTests}

@RunWith(classOf[JUnitRunner])
class RoomDaoDBMigrationTest extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {

  val user_id = "12371289371298731827312371273981283"

  //Reference: Checkout tag 3.46. See ZmsDatabase.dbHelper
  def createLegacyDB() = new ZMessagingDB(Robolectric.application, user_id, DisabledTrackingService)

  def createNewDaoDB(): BaseDaoDB = {
    val userId = UserId(user_id)
    val zms = new ZmsDatabase(userId, Robolectric.application, DisabledTrackingService)
    zms.dbHelper
  }

  scenario("KeyValue migration") {
    val key = "asd"
    val value = "fgh"

    //insert data into old db
    val zMessagingDB = createLegacyDB()
    val sqliteDB: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
    KeyValueDataDao.insertOrReplace(KeyValueData(key, value))(sqliteDB)
    zMessagingDB.close()

    //create new
    val roomDaoDB = createNewDaoDB()
    val roomDB: DB = DB.fromAndroid(roomDaoDB.getWritableDatabase)
    //read value
    KeyValueDataDao.getAll(Set(key))(roomDB).map({readValue =>
      assert(readValue.value == value)
      println("true!")
    })

    //clean up
    roomDaoDB.close()
    Robolectric.application.getDatabasePath(roomDaoDB.getDatabaseName).delete()
  }
}
