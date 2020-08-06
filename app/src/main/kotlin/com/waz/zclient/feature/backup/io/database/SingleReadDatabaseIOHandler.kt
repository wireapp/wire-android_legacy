package com.waz.zclient.feature.backup.io.database

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.feature.backup.io.BatchReader
import com.waz.zclient.feature.backup.io.forEach

class SingleReadDatabaseIOHandler<E>(
    private val singleReadDao: SingleReadDao<E>
) : BackUpIOHandler<E> {

    override suspend fun write(iterator: BatchReader<E>): Either<Failure, Unit> =
        iterator.forEach { requestDatabase { singleReadDao.insert(it) } }

    override fun readIterator(): BatchReader<E> = object : BatchReader<E> {
        var count = 0
        private var items: List<E>? = null

        override suspend fun readNext(): Either<Failure, E?> = requestDatabase {
            if (items == null) {
                items = singleReadDao.allItems()
            }
            items!!.getOrNull(count)?.also { count++ }
        }
    }
}

interface SingleReadDao<E> {
    suspend fun insert(item: E)
    suspend fun allItems(): List<E>
}
