package com.waz.zclient.feature.backup.io.database

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.feature.backup.io.BatchReader
import com.waz.zclient.feature.backup.io.mapRight

class BatchDatabaseIOHandler<E>(
    private val batchReadableDao: BatchReadableDao<E>,
    private val batchSize: Int = BATCH_SIZE
) : BackUpIOHandler<E, Unit> {
    override suspend fun write(iterator: BatchReader<List<E>>): Either<Failure, List<Unit>> =
        iterator.mapRight {
            batchReadableDao.insertAll(it)
            Either.Right(Unit)
        }

    override fun readIterator(): BatchReader<List<E>> = object : BatchReader<List<E>> {
        private var count = 0
        override suspend fun readNext(): Either<Failure, List<E>?> = requestDatabase {
            batchReadableDao.nextBatch(count, Math.min(batchReadableDao.count() - count, batchSize)).also {
                count += it?.size ?: 0
            }
        }

        override suspend fun hasNext(): Boolean = batchReadableDao.count() > count
    }

    companion object {
        private const val BATCH_SIZE = 1000
    }
}

interface BatchReadableDao<E> {
    suspend fun count(): Int

    suspend fun nextBatch(start: Int, batchSize: Int): List<E>?

    suspend fun insert(item: E)

    suspend fun insertAll(items: List<E>) {
        items.forEach { insert(it) }
    }
}
