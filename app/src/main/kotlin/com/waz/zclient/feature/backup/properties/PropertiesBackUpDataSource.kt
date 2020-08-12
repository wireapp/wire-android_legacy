package com.waz.zclient.feature.backup.properties

import com.waz.zclient.core.extension.empty
import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.property.PropertiesEntity
import kotlinx.serialization.Serializable
import java.io.File

class PropertiesBackUpDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<PropertiesEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<PropertiesBackUpModel, File>,
    override val mapper: BackUpDataMapper<PropertiesBackUpModel, PropertiesEntity>
) : BackUpDataSource<PropertiesBackUpModel, PropertiesEntity>()

@Serializable
data class PropertiesBackUpModel(
    val key: String,
    val value: String = String.empty()
)

class PropertiesBackUpMapper : BackUpDataMapper<PropertiesBackUpModel, PropertiesEntity> {
    override fun fromEntity(entity: PropertiesEntity) =
        PropertiesBackUpModel(key = entity.key, value = entity.value)

    override fun toEntity(model: PropertiesBackUpModel) =
        PropertiesEntity(key = model.key, value = model.value)
}
