package com.waz.zclient.storage.userdatabase.messages

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper


class MessageDeletionTableTestHelper private constructor() {

    companion object {

        private const val MESSAGE_DELETION_TABLE_NAME = "MsgDeletion"
        private const val MESSAGE_DELETION_MESSAGE_ID_COL = "message_id"
        private const val MESSAGE_DELETION_TIMESTAMP_COL = "timestamp"

        fun insertMessageDeletion(messageId: String, timestamp: Int, openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(MESSAGE_DELETION_MESSAGE_ID_COL, messageId)
                it.put(MESSAGE_DELETION_TIMESTAMP_COL, timestamp)
            }

            openHelper.insertWithOnConflict(
                tableName = MESSAGE_DELETION_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
