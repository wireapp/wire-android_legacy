package com.waz.zclient.storage.userdatabase.messages

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper


class MessageContentIndexTableTestHelper private constructor() {

    companion object {
        private const val MESSAGES_CONTENT_INDEX_TABLE_NAME = "MessageContentIndex"
        private const val MESSAGES_CONTENT_INDEX_MESSAGE_ID_COL = "message_id"
        private const val MESSAGES_CONTENT_INDEX_CONV_ID_COL = "conv_id"
        private const val MESSAGES_CONTENT_INDEX_CONTENT_COL = "content"
        private const val MESSAGES_CONTENT_INDEX_TIME_COL = "time"

        fun insertMessageContentIndex(messageId: String,
                                      conversationId: String,
                                      content: String,
                                      timestamp: Int, openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(MESSAGES_CONTENT_INDEX_MESSAGE_ID_COL, messageId)
                it.put(MESSAGES_CONTENT_INDEX_CONV_ID_COL, conversationId)
                it.put(MESSAGES_CONTENT_INDEX_CONTENT_COL, content)
                it.put(MESSAGES_CONTENT_INDEX_TIME_COL, timestamp)

            }

            openHelper.insertWithOnConflict(
                tableName = MESSAGES_CONTENT_INDEX_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
