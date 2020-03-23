package com.waz.zclient.storage.userdatabase.contact

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper

class ContactsTableTestHelper private constructor() {

    companion object {

        private const val CONTACTS_TABLE_NAME = "Contacts"
        private const val CONTACTS_ID_COL = "_id"
        private const val CONTACTS_NAME_COL = "name"
        private const val CONTACTS_NAME_SOURCE_COL = "name_source"
        private const val CONTACTS_SORT_KEY_COL = "sort_key"
        private const val CONTACTS_SEARCH_KEY_COL = "search_key"

        fun insertContact(id: String, name: String, nameType: Int, sortKey: String,
                          searchKey: String, openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(CONTACTS_ID_COL, id)
                it.put(CONTACTS_NAME_COL, name)
                it.put(CONTACTS_NAME_SOURCE_COL, nameType)
                it.put(CONTACTS_SORT_KEY_COL, sortKey)
                it.put(CONTACTS_SEARCH_KEY_COL, searchKey)
            }

            openHelper.insertWithOnConflict(
                tableName = CONTACTS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
