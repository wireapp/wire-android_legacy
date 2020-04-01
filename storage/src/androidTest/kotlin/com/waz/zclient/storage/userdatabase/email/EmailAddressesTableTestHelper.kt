package com.waz.zclient.storage.userdatabase.email

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper


class EmailAddressesTableTestHelper private constructor() {

    companion object {

        private const val EMAIL_ADDRESSES_TABLE_NAME = "EmailAddresses"
        private const val EMAIL_ADDRESSES_CONTACT_ID_COL = "contact"
        private const val EMAIL_ADDRESSES_EMAIL_COL = "email_address"

        fun insertEmailAddress(contactId: String, email: String, openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(EMAIL_ADDRESSES_CONTACT_ID_COL, contactId)
                it.put(EMAIL_ADDRESSES_EMAIL_COL, email)
            }
            openHelper.insertWithOnConflict(
                tableName = EMAIL_ADDRESSES_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
