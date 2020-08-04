package com.waz.zclient.feature.backup.keyvalues

import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.property.KeyValuesEntity
import kotlinx.serialization.Serializable
import java.io.File

class KeyValuesBackUpDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<KeyValuesEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<KeyValuesBackUpModel, File>,
    override val mapper: BackUpDataMapper<KeyValuesBackUpModel, KeyValuesEntity>
) : BackUpDataSource<KeyValuesBackUpModel, KeyValuesEntity>()

@Serializable
data class KeyValuesBackUpModel(val key: String, val value: String)

class KeyValuesBackUpMapper : BackUpDataMapper<KeyValuesBackUpModel, KeyValuesEntity> {
    override fun fromEntity(entity: KeyValuesEntity) =
        KeyValuesBackUpModel(key = entity.key, value = entity.value)

    override fun toEntity(model: KeyValuesBackUpModel) =
        KeyValuesEntity(key = model.key, value = model.value)
}
