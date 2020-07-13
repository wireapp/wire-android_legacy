package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.property.PropertiesDao
import com.waz.zclient.storage.db.property.PropertiesEntity
import kotlinx.serialization.Serializable

class PropertiesLocalDataSource(private val propertiesDao: PropertiesDao): BackupLocalDataSource<PropertiesEntity>() {
    override suspend fun getAll(): List<PropertiesEntity> = propertiesDao.allProperties()

    override fun serialize(entity: PropertiesEntity): String =
        json.stringify(PropertiesJSONEntity.serializer(), PropertiesJSONEntity.from(entity))
    override fun deserialize(jsonStr: String): PropertiesEntity =
        json.parse(PropertiesJSONEntity.serializer(), jsonStr).toEntity()
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
