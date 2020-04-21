package com.waz.db.migration.userdatabase

import com.waz.db.migration.KotlinMigrationHelper
import com.waz.model.Contact.ContactsDao
import com.waz.model._
import com.waz.service.SearchKey
import com.waz.utils.wrappers.{DB, SQLiteDBWrapper}
import com.waz.zclient.storage.db.contacts.ContactsEntity

class ContactsMigrationTest extends UserDatabaseMigrationTest {
  feature("Contacts table migration") {
    scenario("Contacts migration with default values") {
      implicit val db: DB = new SQLiteDBWrapper(zMessagingDB.getWritableDatabase)
      val contact = Contact(ContactId(), "name", NameSource.StructuredName, "", SearchKey(""), Set.empty[PhoneNumber], Set.empty[EmailAddress])
      ContactsDao.insertOrReplace(Seq(contact))
      closeDB()
      withRoomDB({
        KotlinMigrationHelper.assertContactsEntity(_, new ContactsEntity(
          contact.id.str, "name", 2, "", ""
        ))
      })
    }
  }
}
