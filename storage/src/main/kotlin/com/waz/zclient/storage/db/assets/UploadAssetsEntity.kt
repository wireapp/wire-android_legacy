package com.waz.zclient.storage.db.assets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "UploadAssets")
data class UploadAssetsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "source", defaultValue = "")
    val source: String,

    @ColumnInfo(name = "name", defaultValue = "")
    val name: String,

    @ColumnInfo(name = "sha", typeAffinity = ColumnInfo.BLOB)
    val sha: ByteArray?,

    @ColumnInfo(name = "md5", typeAffinity = ColumnInfo.BLOB)
    val md5: ByteArray?,

    @ColumnInfo(name = "mime", defaultValue = "")
    val mime: String,

    @ColumnInfo(name = "preview", defaultValue = "")
    val preview: String,

    @ColumnInfo(name = "uploaded", defaultValue = "0")
    val uploaded: Long,

    @ColumnInfo(name = "size", defaultValue = "0")
    val size: Long,

    @ColumnInfo(name = "retention", defaultValue = "0")
    val retention: Int,

    @ColumnInfo(name = "public", defaultValue = "0")
    val isPublic: Boolean,

    @ColumnInfo(name = "encryption", defaultValue = "")
    val encryption: String,

    @ColumnInfo(name = "encryption_salt")
    val encryptionSalt: String?,

    @ColumnInfo(name = "details", defaultValue = "")
    val details: String,

    @ColumnInfo(name = "status", defaultValue = "0")
    val uploadStatus: Int,

    @ColumnInfo(name = "asset_id")
    val assetId: String?
)
