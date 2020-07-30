package com.waz.zclient.feature.backup

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.backup.io.BatchReader

interface BackUpIOHandler<T> {
    suspend fun write(iterator: BatchReader<T>): Either<Failure, Unit>
    fun readIterator(): BatchReader<T>
}

interface BackUpDataMapper<T, E> {
    fun fromEntity(entity: E): T
    fun toEntity(model: T): E
}
