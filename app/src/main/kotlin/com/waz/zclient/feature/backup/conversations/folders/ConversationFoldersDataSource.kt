package com.waz.zclient.feature.backup.conversations.folders

import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.folders.FoldersEntity
import kotlinx.serialization.Serializable

@Serializable
data class FoldersBackUpModel(
    val id: String,
    val name: String,
    val type: Int
)

class FoldersBackupMapper : BackUpDataMapper<FoldersBackUpModel, FoldersEntity> {
    override fun fromEntity(entity: FoldersEntity) =
        FoldersBackUpModel(id = entity.id, name = entity.name, type = entity.type)

    override fun toEntity(model: FoldersBackUpModel) =
        FoldersEntity(id = model.id, name = model.name, type = model.type)
}

class FoldersBackupDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<FoldersEntity>,
    override val backUpLocalDataSource: BackUpIOHandler<FoldersBackUpModel>,
    override val mapper: BackUpDataMapper<FoldersBackUpModel, FoldersEntity>
) : BackUpDataSource<FoldersBackUpModel, FoldersEntity>()
