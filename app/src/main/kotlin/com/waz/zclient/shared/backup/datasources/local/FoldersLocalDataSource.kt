package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.folders.FoldersDao
import com.waz.zclient.storage.db.folders.FoldersEntity
import kotlinx.serialization.Serializable

class FoldersLocalDataSource(private val foldersDao: FoldersDao): BackupLocalDataSource<FoldersEntity>() {
    override suspend fun getAll(): List<FoldersEntity> = foldersDao.allFolders()
    override suspend fun getInBatch(batchSize: Int, offset: Int): List<FoldersEntity> =
        foldersDao.getFoldersInBatch(batchSize, offset)

    override fun serialize(entity: FoldersEntity): String =
        json.stringify(FoldersJSONEntity.serializer(), FoldersJSONEntity.from(entity))
    override fun deserialize(jsonStr: String): FoldersEntity =
        json.parse(FoldersJSONEntity.serializer(), jsonStr).toEntity()
}

@Serializable
data class FoldersJSONEntity(
    val id: String,
    val name: String = "",
    val type: Int = 0
) {
    fun toEntity(): FoldersEntity = FoldersEntity(
        id = id,
        name = name,
        type = type
    )

    companion object {
        fun from(entity: FoldersEntity): FoldersJSONEntity = FoldersJSONEntity(
            id = entity.id,
            name = entity.name,
            type = entity.type
        )
    }
}
