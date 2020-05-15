package com.waz.zclient.storage.userdatabase.conversations

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper

class ConversationFoldersTableTestHelper private constructor() {

    companion object {
        private const val CONVERSATION_FOLDERS_TABLE_NAME = "ConversationFolders"
        private const val CONVERSATION_ID_COL = "conv_id"
        private const val FOLDER_ID_COL = "folder_id"

        fun insertConversationFolder(
            convId: String,
            folderId: String,
            openHelper: DbSQLiteOpenHelper
        ) {
            val contentValues = ContentValues().also {
                it.put(CONVERSATION_ID_COL, convId)
                it.put(FOLDER_ID_COL, folderId)
            }

            openHelper.insertWithOnConflict(
                tableName = CONVERSATION_FOLDERS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
