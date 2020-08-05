package com.waz.zclient.feature.backup.io.database

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.feature.backup.io.BatchReader
import com.waz.zclient.feature.backup.io.forEach

class BatchDatabaseIOHandler<E>(
    private val batchReadableDao: BatchReadableDao<E>,
    private val batchSize: Int
) : BackUpIOHandler<E> {

    override suspend fun write(iterator: BatchReader<E>): Either<Failure, Unit> =
        iterator.forEach {
            requestDatabase {
                //TODO write in batches with a buffered approach (for performance)
                batchReadableDao.insert(it)
            }
        }

    override fun readIterator(): BatchReader<E> = object : BatchReader<E> {
        var count = 0
        val currentBatch = mutableListOf<E>()

        override suspend fun readNext(): Either<Failure, E?> = requestDatabase {
            if (count % batchSize == 0) {
                currentBatch.clear()
                currentBatch.addAll(batchReadableDao.nextBatch(
                    start = count,
                    batchSize = (batchReadableDao.count() - count).coerceAtMost(batchSize)
                ) ?: emptyList())
            }
            currentBatch[count % batchSize].also {
                count++
            }
        }
    }
}

interface BatchReadableDao<E> {
    suspend fun count(): Int

    suspend fun nextBatch(start: Int, batchSize: Int): List<E>?

    suspend fun insert(item: E)
}
