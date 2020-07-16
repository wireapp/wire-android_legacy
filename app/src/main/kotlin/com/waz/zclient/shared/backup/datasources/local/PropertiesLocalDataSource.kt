package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.property.PropertiesDao
import com.waz.zclient.storage.db.property.PropertiesEntity
import kotlinx.serialization.Serializable

class PropertiesLocalDataSource(private val propertiesDao: PropertiesDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<PropertiesEntity, PropertiesJSONEntity>(PropertiesJSONEntity.serializer(), batchSize) {
    override suspend fun getInBatch(batchSize: Int, offset: Int): List<PropertiesEntity> =
        propertiesDao.getPropertiesInBatch(batchSize, offset)

    override fun toJSON(entity: PropertiesEntity): PropertiesJSONEntity = PropertiesJSONEntity.from(entity)
    override fun toEntity(json: PropertiesJSONEntity): PropertiesEntity = json.toEntity()
}

@Serializable
data class PropertiesJSONEntity(
    val key: String,
    val value: String = ""
) {
    fun toEntity(): PropertiesEntity = PropertiesEntity(
        key = key,
        value = value
    )

    companion object {
        fun from(entity: PropertiesEntity): PropertiesJSONEntity = PropertiesJSONEntity(
            key = entity.key,
            value = entity.value
        )
    }
}
