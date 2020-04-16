package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.AddressBook.{ContactHashes, ContactHashesDao}
import com.waz.model.ContactId
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.contacts.ContactHashesEntity

class ContactHashesMigrationTest extends UserDatabaseMigrationTest {
  feature("ContactHashes table migration") {
    scenario("ContactHashes migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val contactHashes = ContactHashes(ContactId(), Set.empty[String])
      ContactHashesDao.insertOrReplace(Seq(contactHashes))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertContactHashesEntity(_, new ContactHashesEntity(
          contactHashes.id.str, null
        ))
      })
    }
  }
}
