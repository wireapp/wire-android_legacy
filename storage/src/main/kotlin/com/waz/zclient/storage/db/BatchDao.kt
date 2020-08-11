package com.waz.zclient.storage.db

import androidx.room.Insert

interface BatchDao<E> {

    suspend fun count(): Int

    suspend fun nextBatch(start: Int, batchSize: Int): List<@JvmSuppressWildcards E>?

    @Insert
    suspend fun insert(item: E)

    @Insert
    @JvmSuppressWildcards
    suspend fun insertAll(items: List<E>)
}
