package com.waz.db.migration

import com.waz.KotlinMigrationHelper
import com.waz.content.{PropertiesDao, PropertyValue}
import com.waz.service.PropertyKey
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.property.PropertiesEntity

class PropertiesMigrationTest extends MigrationTest {
  feature("Properties table migration") {
    scenario("Properties migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val property = PropertyValue(PropertyKey(""), "123")
      PropertiesDao.insertOrReplace(Seq(property))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertPropertiesEntity(_, new PropertiesEntity("", "\"123\""))
      })
    }
  }
}
