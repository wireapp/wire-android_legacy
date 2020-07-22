package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.property.PropertiesDao
import com.waz.zclient.storage.db.property.PropertiesEntity
import kotlinx.serialization.Serializable

class PropertiesLocalDataSource(dao: PropertiesDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<PropertiesEntity, PropertiesJSONEntity>("properties", dao, batchSize, PropertiesJSONEntity.serializer()) {
    override fun toJSON(entity: PropertiesEntity) = PropertiesJSONEntity.from(entity)
    override fun toEntity(json: PropertiesJSONEntity) = json.toEntity()
}

@Serializable
data class PropertiesJSONEntity(val key: String, val value: String = "") {
    fun toEntity() = PropertiesEntity(key, value)

    companion object {
        fun from(entity: PropertiesEntity) = PropertiesJSONEntity(entity.key, entity.value)
    }
}
