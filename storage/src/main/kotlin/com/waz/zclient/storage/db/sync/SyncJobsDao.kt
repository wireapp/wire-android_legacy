package com.waz.zclient.storage.db.sync

import androidx.room.Dao
import androidx.room.Query

@Dao
interface SyncJobsDao {
    @Query("SELECT * FROM SyncJobs")
    suspend fun allSyncJobs(): List<SyncJobsEntity>
}
