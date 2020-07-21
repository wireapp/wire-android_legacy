package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.folders.FoldersDao
import com.waz.zclient.storage.db.folders.FoldersEntity
import kotlinx.serialization.Serializable

class FoldersLocalDataSource(dao: FoldersDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<FoldersEntity, FoldersJSONEntity>("folders", dao, batchSize, FoldersJSONEntity.serializer()) {
    override fun toJSON(entity: FoldersEntity) = FoldersJSONEntity.from(entity)
    override fun toEntity(json: FoldersJSONEntity) = json.toEntity()
}

@Serializable
data class FoldersJSONEntity(
    val id: String,
    val name: String = "",
    val type: Int = 0
) {
    fun toEntity() = FoldersEntity(
        id = id,
        name = name,
        type = type
    )

    companion object {
        fun from(entity: FoldersEntity) = FoldersJSONEntity(
            id = entity.id,
            name = entity.name,
            type = entity.type
        )
    }
}
