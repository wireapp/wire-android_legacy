package com.waz.zclient.storage.userdatabase.assets

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper

class UploadAssetsTableTestHelper private constructor() {

    companion object {
        private const val UPLOAD_ASSETS_TABLE_NAME = "UploadAssets"
        private const val UPLOAD_ASSET_ID_COL = "_id"
        private const val SOURCE_COL = "source"
        private const val NAME_COL = "name"
        private const val SHA_COL = "sha"
        private const val MD5_COL = "md5"
        private const val MIME_COL = "mime"
        private const val PREVIEW_COL = "preview"
        private const val UPLOADED_COL = "uploaded"
        private const val SIZE_COL = "size"
        private const val RETENTION_COL = "retention"
        private const val IS_PUBLIC_COL = "public"
        private const val ENCRYPTION_COL = "encryption"
        private const val ENCRYPTION_SALT_COL = "encryption_salt"
        private const val DETAILS_COL = "details"
        private const val UPLOAD_STATUS_COL = "status"
        private const val ASSETS_ID_COL = "asset_id"

        fun insertUploadAsset(
            id: String,
            source: String,
            name: String,
            sha: ByteArray,
            md5: ByteArray,
            mime: String,
            preview: String,
            uploaded: Long,
            size: Long,
            retention: Int,
            isPublic: Boolean,
            encryption: String,
            encryptionSalt: String?,
            details: String,
            uploadStatus: Int,
            assetId: String?,
            openHelper: DbSQLiteOpenHelper
        ) {
            val contentValues = ContentValues().also {
                it.put(UPLOAD_ASSET_ID_COL, id)
                it.put(SOURCE_COL, source)
                it.put(NAME_COL, name)
                it.put(SHA_COL, sha)
                it.put(MD5_COL, md5)
                it.put(MIME_COL, mime)
                it.put(PREVIEW_COL, preview)
                it.put(UPLOADED_COL, uploaded)
                it.put(SIZE_COL, size)
                it.put(RETENTION_COL, retention)
                it.put(IS_PUBLIC_COL, isPublic)
                it.put(ENCRYPTION_COL, encryption)
                it.put(ENCRYPTION_SALT_COL, encryptionSalt)
                it.put(DETAILS_COL, details)
                it.put(UPLOAD_STATUS_COL, uploadStatus)
                it.put(ASSETS_ID_COL, assetId)
            }

            openHelper.insertWithOnConflict(
                tableName = UPLOAD_ASSETS_TABLE_NAME,
                contentValues = contentValues
            )

        }
    }
}
