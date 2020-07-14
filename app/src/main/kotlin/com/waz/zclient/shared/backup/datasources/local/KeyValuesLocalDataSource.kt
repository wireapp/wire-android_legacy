package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.property.KeyValuesDao
import com.waz.zclient.storage.db.property.KeyValuesEntity
import kotlinx.serialization.Serializable

class KeyValuesLocalDataSource(private val keyValuesDao: KeyValuesDao): BackupLocalDataSource<KeyValuesEntity>() {
    override suspend fun getAll(): List<KeyValuesEntity> = keyValuesDao.allKeyValues()
    override suspend fun getInBatch(batchSize: Int, offset: Int): List<KeyValuesEntity> =
        keyValuesDao.getKeyValuesInBatch(batchSize, offset)

    override fun serialize(entity: KeyValuesEntity): String =
        json.stringify(KeyValuesJSONEntity.serializer(), KeyValuesJSONEntity.from(entity))
    override fun deserialize(jsonStr: String): KeyValuesEntity =
        json.parse(KeyValuesJSONEntity.serializer(), jsonStr).toEntity()
}

@Serializable
data class KeyValuesJSONEntity(
    val key: String,
    val value: String = ""
) {
    fun toEntity(): KeyValuesEntity = KeyValuesEntity(
        key = key,
        value = value
    )

    companion object {
        fun from(entity: KeyValuesEntity): KeyValuesJSONEntity = KeyValuesJSONEntity(
            key = entity.key,
            value = entity.value
        )
    }
}
