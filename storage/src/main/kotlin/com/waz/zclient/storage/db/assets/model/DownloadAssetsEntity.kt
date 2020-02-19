package com.waz.zclient.storage.db.assets.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "DownloadAssets")
data class DownloadAssetsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "mime")
    var mime: String?,

    @ColumnInfo(name = "downloaded")
    var downloaded: Long?,

    @ColumnInfo(name = "size")
    var size: Long?,

    @ColumnInfo(name = "name")
    var name: String?,

    @ColumnInfo(name = "preview")
    var preview: String?,

    @ColumnInfo(name = "details")
    var details: String?,

    @ColumnInfo(name = "status")
    var status: Int
)
