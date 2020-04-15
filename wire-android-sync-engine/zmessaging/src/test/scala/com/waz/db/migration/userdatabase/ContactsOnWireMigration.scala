package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.Contact.ContactsOnWireDao
import com.waz.model.{ContactId, UserId}
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.contacts.ContactsOnWireEntity

class ContactsOnWireMigration extends UserDatabaseMigrationTest {
  feature("ContactsOnWire table migration") {
    scenario("ContactsOnWire migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val contactData: (UserId, ContactId) = (UserId(), ContactId())
      ContactsOnWireDao.insertOrReplace(Seq(contactData))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertContactsOnWireEntity(_, new ContactsOnWireEntity(
          contactData._1.str, contactData._2.str
        ))
      })
    }
  }
}
