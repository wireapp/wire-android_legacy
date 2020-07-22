package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.property.KeyValuesDao
import com.waz.zclient.storage.db.property.KeyValuesEntity
import kotlinx.serialization.Serializable

class KeyValuesLocalDataSource(dao: KeyValuesDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<KeyValuesEntity, KeyValuesJSONEntity>("keyValues", dao, batchSize, KeyValuesJSONEntity.serializer()) {
    override fun toJSON(entity: KeyValuesEntity) = KeyValuesJSONEntity.from(entity)
    override fun toEntity(json: KeyValuesJSONEntity) = json.toEntity()
}

@Serializable
data class KeyValuesJSONEntity(val key: String, val value: String = "") {
    fun toEntity() = KeyValuesEntity(key, value)

    companion object {
        fun from(entity: KeyValuesEntity) = KeyValuesJSONEntity(entity.key, entity.value)
    }
}
