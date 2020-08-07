package com.waz.zclient.storage.db

interface BatchDao<E> {
    suspend fun count(): Int

    suspend fun nextBatch(start: Int, batchSize: Int): List<E>?

    suspend fun insert(item: E)

    suspend fun insertAll(items: List<E>) {
        items.forEach { insert(it) }
    }
}
