package com.waz.zclient.storage.db.folders

import androidx.room.Dao
import androidx.room.Query
import com.waz.zclient.storage.db.BatchReader

@Dao
interface FoldersDao : BatchReader<FoldersEntity> {
    @Query("SELECT * FROM Folders")
    suspend fun allFolders(): List<FoldersEntity>

    @Query("SELECT * FROM Folders ORDER BY _id LIMIT :batchSize OFFSET :offset")
    override suspend fun getBatch(batchSize: Int, offset: Int): List<FoldersEntity>

    @Query("SELECT COUNT(*) FROM Folders")
    override suspend fun size(): Int
}
