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
