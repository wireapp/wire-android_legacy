package com.waz.zclient.storage.userdatabase.conversations

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper

class ConversationMembersTableTestHelper private constructor() {

    companion object {

        private const val CONVERSATION_MEMBERS_TABLE_NAME = "ConversationMembers"
        private const val USER_ID_COL = "user_id"
        private const val CONVERSATION_ID_COL = "conv_id"
        private const val ROLE_COL = "role"

        fun insertConversationMember(
            userId: String,
            convId: String,
            role: String,
            openHelper: DbSQLiteOpenHelper
        ) {
            val contentValues = ContentValues().also {
                it.put(USER_ID_COL, userId)
                it.put(CONVERSATION_ID_COL, convId)
                it.put(ROLE_COL, role)
            }

            openHelper.insertWithOnConflict(
                tableName = CONVERSATION_MEMBERS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
