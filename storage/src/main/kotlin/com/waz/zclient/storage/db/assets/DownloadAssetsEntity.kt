package com.waz.zclient.storage.db.assets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "DownloadAssets")
data class DownloadAssetsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "mime", defaultValue = "")
    val mime: String,

    @ColumnInfo(name = "downloaded", defaultValue = "0")
    val downloaded: Long,

    @ColumnInfo(name = "size", defaultValue = "0")
    val size: Long,

    @ColumnInfo(name = "name", defaultValue = "")
    val name: String,

    @ColumnInfo(name = "preview", defaultValue = "")
    val preview: String,

    @ColumnInfo(name = "details", defaultValue = "")
    val details: String,

    @ColumnInfo(name = "status", defaultValue = "0")
    val status: Int
)
