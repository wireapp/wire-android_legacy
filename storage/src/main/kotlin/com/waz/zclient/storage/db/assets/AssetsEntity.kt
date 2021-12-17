package com.waz.zclient.storage.db.assets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Assets2")
data class AssetsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "token")
    val token: String?,

    @ColumnInfo(name = "name", defaultValue = "")
    val name: String,

    @ColumnInfo(name = "encryption", defaultValue = "")
    val encryption: String,

    @ColumnInfo(name = "mime", defaultValue = "")
    val mime: String,

    @ColumnInfo(name = "sha", typeAffinity = ColumnInfo.BLOB)
    val sha: ByteArray?,

    @ColumnInfo(name = "size", defaultValue = "0")
    val size: Int,

    @ColumnInfo(name = "source")
    val source: String?,

    @ColumnInfo(name = "preview")
    val preview: String?,

    @ColumnInfo(name = "details", defaultValue = "")
    val details: String
)
