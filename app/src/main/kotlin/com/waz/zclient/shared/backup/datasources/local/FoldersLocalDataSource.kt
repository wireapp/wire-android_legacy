package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.folders.FoldersDao
import com.waz.zclient.storage.db.folders.FoldersEntity
import kotlinx.serialization.Serializable

class FoldersLocalDataSource(private val foldersDao: FoldersDao) {
    suspend fun getAllFolders(): List<FoldersEntity> = foldersDao.allFolders()
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
