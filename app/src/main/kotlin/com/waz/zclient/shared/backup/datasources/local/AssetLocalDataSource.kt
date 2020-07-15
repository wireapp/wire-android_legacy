package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.shared.backup.datasources.local.BackupLocalDataSource.Companion.toByteArray
import com.waz.zclient.shared.backup.datasources.local.BackupLocalDataSource.Companion.toIntArray
import com.waz.zclient.storage.db.assets.AssetsDao
import com.waz.zclient.storage.db.assets.AssetsEntity
import kotlinx.serialization.Serializable

class AssetLocalDataSource(
    private val assetsDao: AssetsDao,
    batchSize: Int = BatchSize
): BackupLocalDataSource<AssetsEntity, AssetsJSONEntity>(AssetsJSONEntity.serializer(), batchSize) {
    override suspend fun getInBatch(batchSize: Int, offset: Int): List<AssetsEntity> =
        assetsDao.getAssetsInBatch(batchSize, offset)

    override fun toJSON(entity: AssetsEntity): AssetsJSONEntity = AssetsJSONEntity.from(entity)
    override fun toEntity(json: AssetsJSONEntity): AssetsEntity = json.toEntity()
}

@SuppressWarnings("ComplexMethod")
@Serializable
data class AssetsJSONEntity(
        val id: String,
        val token: String? = null,
        val name: String = "",
        val encryption: String = "",
        val mime: String = "",
        val sha: IntArray? = null,
        val size: Int = 0,
        val source: String? = null,
        val preview: String? = null,
        val details: String = "",
        val conversationId: String? = null
) {
    override fun hashCode(): Int =
        id.hashCode() + token.hashCode() + name.hashCode() + encryption.hashCode() +
        mime.hashCode() + size.hashCode() + source.hashCode() + preview.hashCode() +
        details.hashCode() + conversationId.hashCode() + (sha?.size ?: 0)

    override fun equals(other: Any?): Boolean =
        other != null && other is AssetsJSONEntity && other.id == id && other.token == token &&
        other.name == name && other.encryption == encryption && other.mime == mime &&
        other.size == size && other.source == source && other.preview == preview &&
        other.details == details && other.conversationId == conversationId &&
        ((other.sha == null && sha == null) || other.sha != null && sha !== null &&
            other.sha.zip(sha).all { it.first == it.second }
        )

    fun toEntity(): AssetsEntity = AssetsEntity(
        id = id,
        token = token,
        name = name,
        encryption = encryption,
        mime = mime,
        sha = toByteArray(sha),
        size = size,
        source = source,
        preview = preview,
        details = details,
        conversationId = conversationId
    )

    companion object {
        fun from(entity: AssetsEntity): AssetsJSONEntity = AssetsJSONEntity(
            id = entity.id,
            token = entity.token,
            name = entity.name,
            encryption = entity.encryption,
            mime = entity.mime,
            sha = toIntArray(entity.sha),
            size = entity.size,
            source = entity.source,
            preview = entity.preview,
            details = entity.details,
            conversationId = entity.conversationId
        )
    }
}
