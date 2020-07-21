package com.waz.zclient.storage.db

interface BatchReader<EntityType> {
    suspend fun getBatch(batchSize: Int, offset: Int): List<@JvmSuppressWildcards EntityType>?

    suspend fun size(): Int
}
