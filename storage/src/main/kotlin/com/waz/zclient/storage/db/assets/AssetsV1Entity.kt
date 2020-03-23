package com.waz.zclient.storage.db.assets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Assets")
data class AssetsV1Entity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "asset_type")
    val assetType: String,

    @ColumnInfo(name = "data")
    val data: String
)
