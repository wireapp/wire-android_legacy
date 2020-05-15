package com.waz.zclient.storage.db.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CacheEntryDao {

    @Query("SELECT * FROM CacheEntry")
    suspend fun cacheEntries(): List<CacheEntryEntity>

    @Insert
    suspend fun insertCacheEntry(cacheEntry: CacheEntryEntity)
}
