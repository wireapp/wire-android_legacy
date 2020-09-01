package com.waz.zclient.storage.db

import androidx.room.Insert
import androidx.room.OnConflictStrategy

interface BatchDao<E> {

    suspend fun count(): Int

    suspend fun nextBatch(start: Int, batchSize: Int): List<@JvmSuppressWildcards E>?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: E)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @JvmSuppressWildcards
    suspend fun insertAll(items: List<E>)
}
