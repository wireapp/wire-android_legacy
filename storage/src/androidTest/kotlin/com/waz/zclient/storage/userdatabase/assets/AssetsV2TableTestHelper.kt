package com.waz.zclient.storage.userdatabase.assets

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper

class AssetsV2TableTestHelper private constructor() {

    companion object {

        private const val ASSETS_TABLE_NAME = "Assets2"
        private const val ASSETS_ID_COL = "_id"
        private const val TOKEN_COL = "token"
        private const val NAME_COL = "name"
        private const val ENCRYPTION_COL = "encryption"
        private const val MIME_COL = "mime"
        private const val SHA_COL = "sha"
        private const val SIZE_COL = "size"
        private const val SOURCE_COL = "source"
        private const val PREVIEW_COL = "preview"
        private const val DETAILS_COL = "details"
        private const val CONVERSATION_ID_COL = "conversation_id"

        fun insertV2Asset(
            id: String,
            token: String?,
            name: String,
            encryption: String,
            mime: String,
            sha: ByteArray,
            size: Int,
            source: String?,
            preview: String?,
            details: String,
            conversationId: String?,
            openHelper: DbSQLiteOpenHelper
        ) {
            val contentValues = ContentValues().also {
                it.put(ASSETS_ID_COL, id)
                it.put(TOKEN_COL, token)
                it.put(NAME_COL, name)
                it.put(ENCRYPTION_COL, encryption)
                it.put(MIME_COL, mime)
                it.put(SHA_COL, sha)
                it.put(SIZE_COL, size)
                it.put(SOURCE_COL, source)
                it.put(PREVIEW_COL, preview)
                it.put(DETAILS_COL, details)
                it.put(CONVERSATION_ID_COL, conversationId)
            }

            openHelper.insertWithOnConflict(
                tableName = ASSETS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }

}
