package com.waz.zclient.core.extension

import com.waz.zclient.core.functional.Either

/**
 * Applies fnL if this is a Left or fnR if this is a Right.
 * @see Left
 * @see Right
 */
suspend fun <L, R, T> Either<L, R>.foldSuspendable(fnL: suspend (L) -> T?, fnR: suspend (R) -> T?): T? =
    when (this) {
        is Either.Left -> fnL(a)
        is Either.Right -> fnR(b)
    }

/**
 * Maps over an iterable of T where the mapping function returns an Either<L, R>.
 * If the mapping function returns Left<L>, the whole mapRight is interrupted and
 * the left value is returned. If the mapping function returns Right for all elements, the result
 * is Right<List<R>>. You can think of this method as a functional version of a map wrapped in
 * a try/catch because the mapping function can throw an exception.
 */
fun <T, L, R> Iterable<T>.mapRight(fn: (T) -> Either<L, R>): Either<L, List<R>> {
    val rightValues = mutableListOf<R>()
    var leftValue: L? = null

    val it = this.iterator()
    while (it.hasNext() && leftValue == null) {
        val value = it.next()
        if (value != null) {
            when (val res = fn(value)) {
                is Either.Right -> rightValues += res.b
                is Either.Left -> leftValue = res.a
            }
        }
    }

    return if (leftValue != null) Either.Left(leftValue) else Either.Right(rightValues.toList())
}
