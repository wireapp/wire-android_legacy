package com.waz.zclient.feature.backup

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.feature.backup.io.BatchReader

abstract class BackUpDataSource<T, E> : BackUpRepository {
    abstract val databaseLocalDataSource: BackUpIOHandler<E>
    abstract val backUpLocalDataSource: BackUpIOHandler<T>
    abstract val mapper: BackUpDataMapper<T, E>

    override suspend fun backUp(): Either<Failure, Unit> {
        val readIterator = databaseLocalDataSource.readIterator()
        val writeIterator: BatchReader<T> = object : BatchReader<T> {
            override suspend fun readNext(): Either<Failure, T?> = readIterator.readNext().map {
                it?.let { mapper.fromEntity(it) }
            }
        }
        return backUpLocalDataSource.write(writeIterator)
    }

    override suspend fun restore(): Either<Failure, Unit> {
        val readIterator = backUpLocalDataSource.readIterator()
        val writeIterator: BatchReader<E> = object : BatchReader<E> {
            override suspend fun readNext(): Either<Failure, E?> = readIterator.readNext().map {
                it?.let { mapper.toEntity(it) }
            }
        }
        return databaseLocalDataSource.write(writeIterator)
    }
}
