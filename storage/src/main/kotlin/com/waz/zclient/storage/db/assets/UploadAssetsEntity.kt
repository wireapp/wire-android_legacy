package com.waz.zclient.storage.db.assets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Objects

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
    val sha: ByteArray?,

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
) {
    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean =
        (other === this) ||
            (other is UploadAssetsEntity &&
                other.id == this.id &&
                other.source == this.source &&
                other.name == this.name &&
                shaEquals(other) &&
                other.md5.contentEquals(this.md5) &&
                other.mime == this.mime &&
                other.preview == this.preview &&
                other.uploaded == this.uploaded &&
                other.size == this.size &&
                other.retention == this.retention &&
                other.isPublic == this.isPublic &&
                other.encryption == this.encryption &&
                other.encryptionSalt == this.encryptionSalt &&
                other.details == this.details &&
                other.uploadStatus == this.uploadStatus &&
                other.assetId == this.assetId)

    override fun hashCode(): Int {
        var result = Objects.hash(id, source, name, mime, preview, uploaded, size, retention,
            isPublic, encryption, encryptionSalt, details, uploadStatus, assetId)
        result = 31 * result + md5.contentHashCode()
        result = 31 * result + (sha?.contentHashCode() ?: 0)
        return result
    }

    private fun shaEquals(other: UploadAssetsEntity): Boolean {
        return if (other.sha == null && this.sha == null) true
        else other.sha != null && this.sha != null && other.sha.contentEquals(this.sha)
    }
}
