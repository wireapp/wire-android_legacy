package com.waz.zclient.feature.backup.io.database

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.feature.backup.io.BatchReader
import com.waz.zclient.feature.backup.io.mapRight
import com.waz.zclient.storage.db.BatchDao

class BatchDatabaseIOHandler<E>(
    private val batchDao: BatchDao<E>,
    private val batchSize: Int = BATCH_SIZE
) : BackUpIOHandler<E, Unit> {
    override suspend fun write(iterator: BatchReader<List<E>>): Either<Failure, List<Unit>> =
        iterator.mapRight {
            batchDao.insertAll(it)
            Either.Right(Unit)
        }

    override fun readIterator(): BatchReader<List<E>> = object : BatchReader<List<E>> {
        private var daoCount: Int? = null

        private suspend fun batchDaoCount(): Int = daoCount ?: batchDao.count().also {
            daoCount = it
        }

        private var count = 0

        override suspend fun readNext(): Either<Failure, List<E>> = requestDatabase {
            val batch = batchDao.nextBatch(count, Math.min(batchDao.count() - count, batchSize)) ?: emptyList()
            count += batch.size
            batch
        }

        override suspend fun hasNext(): Boolean {
            return batchDaoCount() > count
        }
    }

    companion object {
        private const val BATCH_SIZE = 1000
    }
}
