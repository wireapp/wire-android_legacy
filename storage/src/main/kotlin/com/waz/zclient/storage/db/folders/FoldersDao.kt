package com.waz.zclient.storage.db.folders

import androidx.room.Dao
import androidx.room.Query

@Dao
interface FoldersDao {
    @Query("SELECT * FROM Folders")
    suspend fun allFolders(): List<FoldersEntity>
}
