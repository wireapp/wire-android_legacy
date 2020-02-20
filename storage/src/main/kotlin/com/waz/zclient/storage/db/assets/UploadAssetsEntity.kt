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
    var source: String?,

    @ColumnInfo(name = "name")
    var name: String?,

    @ColumnInfo(name = "sha", typeAffinity = ColumnInfo.BLOB)
    var sha: ByteArray?,

    @ColumnInfo(name = "md5", typeAffinity = ColumnInfo.BLOB)
    var md5: ByteArray?,

    @ColumnInfo(name = "mime")
    var mime: String?,

    @ColumnInfo(name = "preview")
    var preview: String?,

    @ColumnInfo(name = "uploaded")
    var uploaded: Long?,

    @ColumnInfo(name = "size")
    var size: Long?,

    @ColumnInfo(name = "retention")
    var retention: Int?,

    @ColumnInfo(name = "public")
    var isPublic: Boolean,

    @ColumnInfo(name = "encryption")
    var encryption: String?,

    @ColumnInfo(name = "encryption_salt")
    var encryptionSalt: String?,

    @ColumnInfo(name = "details")
    var details: String?,

    @ColumnInfo(name = "status")
    var uploadStatus: Int?,

    @ColumnInfo(name = "asset_id")
    var assetId: String?
)
