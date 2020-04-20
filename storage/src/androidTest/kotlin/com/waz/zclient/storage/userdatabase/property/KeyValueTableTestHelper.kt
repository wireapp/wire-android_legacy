package com.waz.zclient.storage.userdatabase.property

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper


class KeyValueTableTestHelper private constructor() {

    companion object {

        private const val KEY_VALUES_TABLE_NAME = "KeyValues"
        private const val KEY_VALUES_KEY_COL = "key"
        private const val KEY_VALUES_VALUE_COL = "value"

        fun insertKeyValue(key: String, value: String, openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(KEY_VALUES_KEY_COL, key)
                it.put(KEY_VALUES_VALUE_COL, value)
            }

            openHelper.insertWithOnConflict(
                tableName = KEY_VALUES_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
