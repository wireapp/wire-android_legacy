package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.property.KeyValuesDao
import com.waz.zclient.storage.db.property.KeyValuesEntity
import kotlinx.serialization.Serializable

class KeyValuesLocalDataSource(dao: KeyValuesDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<KeyValuesEntity, KeyValuesJSONEntity>("keyValues", dao, batchSize, KeyValuesJSONEntity.serializer()) {
    override fun toJSON(entity: KeyValuesEntity): KeyValuesJSONEntity = KeyValuesJSONEntity.from(entity)
    override fun toEntity(json: KeyValuesJSONEntity): KeyValuesEntity = json.toEntity()
}

@Serializable
data class KeyValuesJSONEntity(val key: String, val value: String = "") {
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
