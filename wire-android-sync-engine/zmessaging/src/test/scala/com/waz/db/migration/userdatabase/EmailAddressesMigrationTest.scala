package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.Contact.EmailAddressesDao
import com.waz.model.{ContactId, EmailAddress}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.email.EmailAddressesEntity

class EmailAddressesMigrationTest extends UserDatabaseMigrationTest {
  feature("EmailAddresses table migration") {
    scenario("EmailAddresses migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val emailAddress = EmailAddress("")
      val item = (ContactId(), emailAddress)
      EmailAddressesDao.insertOrReplace(Seq(item))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertEmailAddressEntity(_, new EmailAddressesEntity(
          item._1.str, ""
        ))
      })
    }
  }
}
