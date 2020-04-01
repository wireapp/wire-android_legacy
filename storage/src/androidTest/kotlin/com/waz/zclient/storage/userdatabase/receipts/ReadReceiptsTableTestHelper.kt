package com.waz.zclient.storage.userdatabase.receipts

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper

class ReadReceiptsTableTestHelper private constructor() {

    companion object {

        private const val READ_RECEIPTS_TABLE_NAME = "ReadReceipts"
        private const val READ_RECEIPTS_MESSAGE_ID_COL = "message_id"
        private const val READ_RECEIPTS_USER_ID_COL = "user_id"
        private const val READ_RECEIPTS_TIMESTAMP_COL = "timestamp"

        fun insertReceipt(messageId: String, userId: String, timestamp: Int,
                          openHelper: DbSQLiteOpenHelper) {
            val contentValues = ContentValues().also {
                it.put(READ_RECEIPTS_MESSAGE_ID_COL, messageId)
                it.put(READ_RECEIPTS_USER_ID_COL, userId)
                it.put(READ_RECEIPTS_TIMESTAMP_COL, timestamp)
            }
            openHelper.insertWithOnConflict(
                tableName = READ_RECEIPTS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
