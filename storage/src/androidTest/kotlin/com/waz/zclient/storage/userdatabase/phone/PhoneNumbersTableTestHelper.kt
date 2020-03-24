package com.waz.zclient.storage.userdatabase.phone

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper


class PhoneNumbersTableTestHelper private constructor() {

    companion object {

        private const val EMAIL_ADDRESSES_TABLE_NAME = "PhoneNumbers"
        private const val EMAIL_ADDRESSES_CONTACT_ID_COL = "contact"
        private const val EMAIL_ADDRESSES_PHONE_COL = "phone_number"

        fun insertPhoneNumber(contactId: String, phone: String, openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(EMAIL_ADDRESSES_CONTACT_ID_COL, contactId)
                it.put(EMAIL_ADDRESSES_PHONE_COL, phone)
            }
            openHelper.insertWithOnConflict(
                tableName = EMAIL_ADDRESSES_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
