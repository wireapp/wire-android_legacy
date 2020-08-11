package com.waz.zclient.feature.backup.folders

import com.waz.zclient.core.extension.empty
import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.folders.FoldersEntity
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class FoldersBackUpModel(
    val id: String,
    val name: String = String.empty(),
    val type: Int = 0
)

class FoldersBackupMapper : BackUpDataMapper<FoldersBackUpModel, FoldersEntity> {
    override fun fromEntity(entity: FoldersEntity) =
        FoldersBackUpModel(id = entity.id, name = entity.name, type = entity.type)

    override fun toEntity(model: FoldersBackUpModel) =
        FoldersEntity(id = model.id, name = model.name, type = model.type)
}

class FoldersBackupDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<FoldersEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<FoldersBackUpModel, File>,
    override val mapper: BackUpDataMapper<FoldersBackUpModel, FoldersEntity>
) : BackUpDataSource<FoldersBackUpModel, FoldersEntity>()
