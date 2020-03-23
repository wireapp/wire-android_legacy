package com.waz.zclient.storage.userdatabase.contact

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.waz.zclient.storage.DbSQLiteOpenHelper

class ContactOnWireTableTestHelper private constructor() {

    companion object {

        private const val CONTACTS_ON_WIRE_TABLE_NAME = "ContactsOnWire"
        private const val CONTACTS_ON_WIRE_USER_COL = "user"
        private const val CONTACTS_ON_WIRE_CONTACT_COL = "contact"

        fun insertContactOnWire(userId: String, contactId: String, openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(CONTACTS_ON_WIRE_USER_COL, userId)
                it.put(CONTACTS_ON_WIRE_CONTACT_COL, contactId)
            }

            with(openHelper.writableDatabase) {
                insertWithOnConflict(
                    CONTACTS_ON_WIRE_TABLE_NAME,
                    null,
                    contentValues,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }
}
