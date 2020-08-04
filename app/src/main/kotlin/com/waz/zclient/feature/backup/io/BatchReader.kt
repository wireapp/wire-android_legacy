package com.waz.zclient.feature.backup.io

import com.waz.zclient.core.exception.Failure
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

    suspend fun hasNext(): Boolean
}

/**
 * Reads all items sequentially and applies given [action] to each of them.
 * If [com.waz.zclient.feature.backup.io.BatchReader.readNext] or [action] fails, stops reading
 * more items and immediately returns that [Failure].
 *
 * @return Either.Right(Unit) if all items are read and actions are applied successfully,
 * Either.Left(Failure) otherwise
 */
suspend fun <T, R> BatchReader<T>.forEach(action: suspend (T) -> Either<Failure, R>): Either<Failure, Unit> = mapRight(action).map { Unit }

/**
 * A bit more specific version of [[com.waz.zclient.core.extension.mapRight]].
 * BatchReader does not extend [[Iterable]] and can't use the generic mapRight.
 */
@SuppressWarnings("NestedBlockDepth")
suspend fun <T, R> BatchReader<T>.mapRight(action: suspend (T) -> Either<Failure, R>): Either<Failure, List<R>> {
    val rightValues = mutableListOf<R>()
    var failure: Failure? = null

    while (hasNext() && failure == null) {
        when (val next = readNext()) {
            is Either.Right -> {
                next.b?.let {
                    when (val res = action(it)) {
                        is Either.Right -> rightValues += res.b
                        is Either.Left -> failure = res.a
                    }
                }
            }
            is Either.Left -> failure = next.a
        }
    }

    return if (failure != null) Either.Left(failure!!) else Either.Right(rightValues.toList())
}
