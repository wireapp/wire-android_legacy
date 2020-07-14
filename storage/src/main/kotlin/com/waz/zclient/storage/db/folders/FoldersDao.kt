package com.waz.zclient.storage.db.folders

import androidx.room.Dao
import androidx.room.Query

@Dao
interface FoldersDao {
    @Query("SELECT * FROM Folders")
    suspend fun allFolders(): List<FoldersEntity>

    @Query("SELECT * FROM Folders ORDER BY _id LIMIT :batchSize OFFSET :offset")
    suspend fun getFoldersInBatch(batchSize: Int, offset: Int): List<FoldersEntity>
}
