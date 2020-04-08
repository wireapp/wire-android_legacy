package com.waz.zclient.storage.db.assets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "DownloadAssets")
data class DownloadAssetsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "mime")
    val mime: String,

    @ColumnInfo(name = "downloaded")
    val downloaded: Long,

    @ColumnInfo(name = "size")
    val size: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "preview")
    val preview: String,

    @ColumnInfo(name = "details")
    val details: String,

    @ColumnInfo(name = "status")
    val status: Int
)
