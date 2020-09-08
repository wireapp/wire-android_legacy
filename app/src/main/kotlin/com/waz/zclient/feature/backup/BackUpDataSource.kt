package com.waz.zclient.feature.backup

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.feature.backup.io.BatchReader
import java.io.File

abstract class BackUpDataSource<T, E> : BackUpRepository<List<File>> {
    abstract val databaseLocalDataSource: BackUpIOHandler<E, Unit>
    abstract val backUpLocalDataSource: BackUpIOHandler<T, File>
    abstract val mapper: BackUpDataMapper<T, E>

    override suspend fun saveBackup(): Either<Failure, List<File>> {
        val readIterator = databaseLocalDataSource.readIterator()
        val writeIterator: BatchReader<List<T>> = object : BatchReader<List<T>> {
            override suspend fun readNext(): Either<Failure, List<T>> = readIterator.readNext().map { entities ->
                entities.map { mapper.fromEntity(it) }
            }

            override suspend fun hasNext(): Boolean = readIterator.hasNext()
        }
        return backUpLocalDataSource.write(writeIterator)
    }

    override suspend fun restoreBackup(): Either<Failure, Unit> {
        val readIterator = backUpLocalDataSource.readIterator()
        val writeIterator: BatchReader<List<E>> = object : BatchReader<List<E>> {
            override suspend fun readNext(): Either<Failure, List<E>> = readIterator.readNext().map { models ->
                models.map { mapper.toEntity(it) }
            }

            override suspend fun hasNext(): Boolean = readIterator.hasNext()
        }
        return databaseLocalDataSource.write(writeIterator).map { Unit }
    }
}
