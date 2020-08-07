package com.waz.zclient.storage.db.folders

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.waz.zclient.storage.db.BatchDao

@Dao
interface FoldersDao : BatchDao<FoldersEntity> {

    @Query("SELECT * FROM Folders")
    suspend fun allFolders(): List<FoldersEntity>

    @Insert
    override suspend fun insert(item: FoldersEntity)

    @Query("SELECT * FROM Folders ORDER BY _id LIMIT :batchSize OFFSET :start")
    override suspend fun nextBatch(start: Int, batchSize: Int): List<FoldersEntity>?

    @Query("SELECT COUNT(*) FROM Folders")
    override suspend fun count(): Int

}
