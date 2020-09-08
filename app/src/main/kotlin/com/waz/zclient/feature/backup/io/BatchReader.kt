package com.waz.zclient.feature.backup.io

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess

interface BatchReader<T> {
    /**
     * Reads next item from source and returns the result.
     *
     * @return Either.Left(Failure) if there is an error,
     * Either.Right(t) if next item is read successfully,
     * Either.Right(null) if there are no more items to read.
     */
    suspend fun readNext(): Either<Failure, T>

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
 * Maps over batches of type T where the mapping function returns either a failure or a successful result.
 * If the mapping function returns [Left<Failure>], the whole [mapRight] is interrupted and
 * the left value is returned. If the mapping function returns [Right<R>] for all elements, the total result
 * becomes [Right<List<R>>]. You can think of this method as a functional version of a map wrapped in
 * a try/catch because the mapping function can throw an exception.
 */
suspend fun <T, R> BatchReader<T>.mapRight(action: suspend (T) -> Either<Failure, R>): Either<Failure, List<R>> {
    val rightValues = mutableListOf<R>()
    var failure: Failure? = null

    suspend fun performAction(value: T) =
        action(value).onSuccess { rightValues += it }.onFailure { failure = it }

    while (hasNext() && failure == null) {
        when (val next = readNext()) {
            is Either.Right -> performAction(next.b) // unable to use `onSuccess` because `performAction` is a suspend function
            is Either.Left -> failure = next.a
        }
    }

    return if (failure != null) Either.Left(failure!!) else Either.Right(rightValues.toList())
}
