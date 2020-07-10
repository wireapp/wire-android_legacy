package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.property.KeyValuesDao
import com.waz.zclient.storage.db.property.KeyValuesEntity
import kotlinx.serialization.Serializable

class KeyValueLocalDataSource(private val keyValuesDao: KeyValuesDao) {
    suspend fun getAllKeyValues(): List<KeyValuesEntity> = keyValuesDao.allKeyValues()
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