package com.waz.zclient.storage.userdatabase.messages

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
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

            with(openHelper.writableDatabase) {
                insertWithOnConflict(
                    MESSAGE_DELETION_TABLE_NAME,
                    null,
                    contentValues,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }
}
