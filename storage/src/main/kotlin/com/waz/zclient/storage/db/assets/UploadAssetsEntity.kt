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
) {
    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean =
        (other === this) ||
            (other is UploadAssetsEntity &&
                other.id == this.id &&
                other.source == this.source &&
                other.name == this.name &&
                blobEquals(this.sha, other.sha) &&
                blobEquals(this.md5, other.md5) &&
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
        result = 31 * result + (md5?.contentHashCode() ?: 0)
        result = 31 * result + (sha?.contentHashCode() ?: 0)
        return result
    }

    private fun blobEquals(blob1: ByteArray?, blob2: ByteArray?): Boolean {
        return if (blob2 == null && blob1 == null) true
        else blob2 != null && blob1 != null && blob2.contentEquals(blob1)
    }
}
