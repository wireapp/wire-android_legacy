package com.waz.zclient.storage.db.assets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "UploadAssets")
data class UploadAssetsEntity(
    @PrimaryKey
    @ColumnInfo(name = "_id")
    val id: String,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "sha", typeAffinity = ColumnInfo.BLOB)
    val sha: ByteArray,

    @ColumnInfo(name = "md5", typeAffinity = ColumnInfo.BLOB)
    val md5: ByteArray,

    @ColumnInfo(name = "mime")
    val mime: String,

    @ColumnInfo(name = "preview")
    val preview: String,

    @ColumnInfo(name = "uploaded")
    val uploaded: Long,

    @ColumnInfo(name = "size")
    val size: Long,

    @ColumnInfo(name = "retention")
    val retention: Int,

    @ColumnInfo(name = "public")
    val isPublic: Boolean,

    @ColumnInfo(name = "encryption")
    val encryption: String,

    @ColumnInfo(name = "encryption_salt")
    val encryptionSalt: String?,

    @ColumnInfo(name = "details")
    val details: String,

    @ColumnInfo(name = "status")
    val uploadStatus: Int,

    @ColumnInfo(name = "asset_id")
    val assetId: String?
)
