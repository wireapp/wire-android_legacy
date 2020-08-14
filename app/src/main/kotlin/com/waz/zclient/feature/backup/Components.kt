package com.waz.zclient.feature.backup

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.backup.io.BatchReader
import kotlinx.serialization.SerializationException

interface BackUpIOHandler<T, R> {
    suspend fun write(iterator: BatchReader<List<T>>): Either<Failure, List<R>>
    fun readIterator(): BatchReader<List<T>>
}

interface BackUpDataMapper<T, E> {
    fun fromEntity(entity: E): T
    fun toEntity(model: T): E
}

data class SerializationFailure(val ex: SerializationException) : FeatureFailure()
