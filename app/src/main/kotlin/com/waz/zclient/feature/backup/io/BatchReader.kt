package com.waz.zclient.feature.backup.io

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.extension.foldSuspendable
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map

interface BatchReader<T> {
    /**
     * Reads next item from source and returns the result.
     *
     * @return Either.Left(Failure) if there is an error,
     * Either.Right(t) if next item is read successfully,
     * Either.Right(null) if there are no more items to read.
     */
    suspend fun readNext(): Either<Failure, T?>
}

/**
 * Reads all items sequentially and applies given [action] to each of them.
 * If [com.waz.zclient.feature.backup.io.BatchReader.readNext] or [action] fails, stops reading
 * more items and immediately returns that [Failure].
 *
 * @return Either.Right(Unit) if all items are read and actions are applied successfully,
 * Either.Left(Failure) otherwise
 */
suspend fun <T> BatchReader<T>.forEach(action: suspend (T) -> Either<Failure, Unit>): Either<Failure, Unit> {
    var next = readNext()

    while (next.fold({ false }) { it != null }!!) {
        next.foldSuspendable({}) {
            action(it!!).foldSuspendable({
                next = Either.Left(it)
            }) {
                next = readNext()
                Unit
            }
        }
    }
    return next.map { Unit }
}
