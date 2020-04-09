package com.waz.db.migration

import com.waz.DisabledTrackingService
import com.waz.content.ZmsDatabase
import com.waz.db.BaseDaoDB
import com.waz.model.KeyValueData.KeyValueDataDao
import com.waz.model.{KeyValueData, UserId}
import com.waz.utils.wrappers.DB
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers, RobolectricTests}

@RunWith(classOf[JUnitRunner])
class RoomDaoDBCreationTest extends FeatureSpec with Matchers with BeforeAndAfter with RobolectricTests {

  val user_id = "12371289371298731827312371273981283"

  def createNewDaoDB(): BaseDaoDB = {
    val userId = UserId(user_id)
    val zms = new ZmsDatabase(userId, Robolectric.application, DisabledTrackingService)
    zms.dbHelper
  }

  scenario("KeyValue migration") {
    val key = "asd"
    val value = "fgh"

    //create new
    val roomDaoDB = createNewDaoDB()
    val roomDB: DB = DB.fromAndroid(roomDaoDB.getWritableDatabase)
    //try to insert value
    KeyValueDataDao.insertOrReplace(KeyValueData(key, value))(roomDB)

    //clean up
    roomDaoDB.close()
    Robolectric.application.getDatabasePath(roomDaoDB.getDatabaseName).delete()
  }
}

