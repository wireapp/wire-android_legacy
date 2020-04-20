package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.Contact.PhoneNumbersDao
import com.waz.model.{ContactId, PhoneNumber}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.phone.PhoneNumbersEntity

class PhoneNumbersMigrationTest extends UserDatabaseMigrationTest {
  feature("PhoneNumbers table migration") {
    scenario("PhoneNumbers migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val phoneNumber = PhoneNumber("")
      val item = (ContactId(), phoneNumber)
      PhoneNumbersDao.insertOrReplace(Seq(item))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertPhoneNumbersEntity(_, new PhoneNumbersEntity(
          item._1.str, ""
        ))
      })
    }
  }
}
