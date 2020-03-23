package com.waz.zclient.storage.userdatabase.conversations

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper

class ConversatonsRoleActionTableTestHelper private constructor() {

    companion object {

        private const val CONVERSATION_ROLE_ACTION_TABLE_NAME = "ConversationRoleAction"
        private const val CONVERSATION_ID_COL = "conv_id"
        private const val LABEL_COL = "label"
        private const val ACTION_COL = "action"

        fun insertConversationRoleAction(
            convId: String,
            label: String,
            action: String,
            openHelper: DbSQLiteOpenHelper
        ) {

            val contentValues = ContentValues().also {
                it.put(CONVERSATION_ID_COL, convId)
                it.put(LABEL_COL, label)
                it.put(ACTION_COL, action)
            }
            openHelper.insertWithOnConflict(
                tableName = CONVERSATION_ROLE_ACTION_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
