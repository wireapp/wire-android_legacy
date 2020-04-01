package com.waz.zclient.storage.userdatabase.history

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper

class EditHistoryTableTestHelper private constructor() {

    companion object {

        private const val EDIT_HISTORY_TABLE_NAME = "EditHistory"
        private const val EDIT_HISTORY_ORIGINAL_ID_COL = "original_id"
        private const val EDIT_HISTORY_UPDATED_ID_COL = "updated_id"
        private const val EDIT_HISTORY_TIMESTAMP_COL = "timestamp"

        fun insertHistory(originalId: String, updatedId: String, timestamp: Int,
                          openHelper: DbSQLiteOpenHelper) {
            val contentValues = ContentValues().also {
                it.put(EDIT_HISTORY_ORIGINAL_ID_COL, originalId)
                it.put(EDIT_HISTORY_UPDATED_ID_COL, updatedId)
                it.put(EDIT_HISTORY_TIMESTAMP_COL, timestamp)
            }
            openHelper.insertWithOnConflict(
                tableName = EDIT_HISTORY_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
