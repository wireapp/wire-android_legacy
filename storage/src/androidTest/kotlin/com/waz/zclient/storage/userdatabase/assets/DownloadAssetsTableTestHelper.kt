package com.waz.zclient.storage.userdatabase.assets

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper

class DownloadAssetsTableTestHelper private constructor() {

    companion object {
        private const val DOWNLOAD_ASSETS_TABLE_NAME = "DownloadAssets"
        private const val DOWNLOAD_ASSET_ID_COL = "_id"
        private const val NAME_COL = "name"
        private const val MIME_COL = "mime"
        private const val STATUS_COL = "status"
        private const val SIZE_COL = "size"
        private const val DOWNLOADED_COL = "downloaded"
        private const val PREVIEW_COL = "preview"
        private const val DETAILS_COL = "details"

        fun insertDownloadAsset(
            id: String,
            mime: String,
            downloaded: Long,
            size: Long,
            name: String,
            preview: String,
            details: String,
            status: Int,
            openHelper: DbSQLiteOpenHelper
        ) {
            val contentValues = ContentValues().also {
                it.put(DOWNLOAD_ASSET_ID_COL, id)
                it.put(MIME_COL, mime)
                it.put(DOWNLOADED_COL, downloaded)
                it.put(SIZE_COL, size)
                it.put(NAME_COL, name)
                it.put(PREVIEW_COL, preview)
                it.put(DETAILS_COL, details)
                it.put(STATUS_COL, status)
            }

            openHelper.insertWithOnConflict(
                tableName = DOWNLOAD_ASSETS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
