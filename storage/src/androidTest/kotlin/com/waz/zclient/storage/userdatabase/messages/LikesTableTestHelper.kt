package com.waz.zclient.storage.userdatabase.messages

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper


class LikesTableTestHelper private constructor() {

    companion object {

        private const val LIKES_TABLE_NAME = "Likings"
        private const val LIKES_MESSAGE_ID_COL = "message_id"
        private const val LIKES_USER_ID_COL = "user_id"
        private const val LIKES_TIMESTAMP_COL = "timestamp"
        private const val LIKES_ACTION_COL = "action"

        fun insertLike(messageId: String, userId: String, timestamp: Int,
                       action: Int, openHelper: DbSQLiteOpenHelper) {

            val contentValues = ContentValues().also {
                it.put(LIKES_MESSAGE_ID_COL, messageId)
                it.put(LIKES_USER_ID_COL, userId)
                it.put(LIKES_TIMESTAMP_COL, timestamp)
                it.put(LIKES_ACTION_COL, action)
            }

            openHelper.insertWithOnConflict(
                tableName = LIKES_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
