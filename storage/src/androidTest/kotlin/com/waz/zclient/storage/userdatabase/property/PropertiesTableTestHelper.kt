package com.waz.zclient.storage.userdatabase.property

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.waz.zclient.storage.DbSQLiteOpenHelper


class PropertiesTableTestHelper private constructor() {

    companion object {

        private const val PROPERTIES_TABLE_NAME = "Properties"
        private const val PROPERTIES_KEY_COL = "key"
        private const val PROPERTIES_VALUE_COL = "value"

        fun insertProperty(key: String, value: String, openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(PROPERTIES_KEY_COL, key)
                it.put(PROPERTIES_VALUE_COL, value)
            }

            with(openHelper.writableDatabase) {
                insertWithOnConflict(
                    PROPERTIES_TABLE_NAME,
                    null,
                    contentValues,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }
}
