package com.waz.zclient.storage.db

interface BatchDao<EntityType> {
    suspend fun batch(offset: Int, batchSize: Int): List<@JvmSuppressWildcards EntityType>?

    suspend fun size(): Int
}
