package com.waz.zclient.storage.userdatabase.assets

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper

class AssetsV1TableTestHelper private constructor() {

    companion object {
        private const val ASSETS_TABLE_NAME = "Assets"
        private const val ASSET_ID_COL = "_id"
        private const val ASSET_TYPE_COL = "asset_type"
        private const val ASSET_DATA_COL = "data"

        fun insertV1Asset(
            id: String,
            assetType: String,
            data: String,
            openHelper: DbSQLiteOpenHelper
        ) {
            val contentValues = ContentValues().also {
                it.put(ASSET_ID_COL, id)
                it.put(ASSET_TYPE_COL, assetType)
                it.put(ASSET_DATA_COL, data)
            }

            openHelper.insertWithOnConflict(
                tableName = ASSETS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }

}
