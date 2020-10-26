package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.KeyValueData
import com.waz.model.KeyValueData.KeyValueDataDao
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.property.KeyValuesEntity

class KeyValueMigrationTest extends UserDatabaseMigrationTest {

  scenario("KeyValue migration") {
    implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
    KeyValueDataDao.insertOrReplace(KeyValueData("key", "value"))
    closeDB()
    withRoomDB({ KotlinMigrationHelper.assertKeyValuesEntity(_, new KeyValuesEntity("key","value"))})
  }
}
