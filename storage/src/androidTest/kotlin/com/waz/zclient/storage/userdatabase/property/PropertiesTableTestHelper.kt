package com.waz.zclient.storage.userdatabase.property

import android.content.ContentValues
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

            openHelper.insertWithOnConflict(
                tableName = PROPERTIES_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
