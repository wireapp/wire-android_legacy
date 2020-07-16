package com.waz.zclient.core.utilities

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import kotlinx.coroutines.runBlocking

interface SuspendIterable<T> {
    fun iterator(): SuspendIterator<T>
}

interface SuspendIterator<T> {
    suspend fun next(): T?
}

fun <T> SuspendIterable<T>.foreach(fn: (T) -> Unit) {
    val it = this.iterator()
    var finished = false

    do {
        val value = runBlocking { it.next() }
        if (value != null) fn(value) else finished = true
    } while (!finished)
}

fun <T, S> SuspendIterable<T>.map(fn: (T) -> S): List<S> {
    val it = this.iterator()
    var finished = false
    val results = mutableListOf<S>()

    do {
        val value = runBlocking { it.next() }
        if (value != null) results += fn(value) else finished = true
    } while (!finished)

    return results.toList()
}

fun <T, S> SuspendIterable<T>.mapOrFail(fn: (T) -> Either<Failure, S>): Either<Failure, List<S>> {
    val it = this.iterator()
    var finished = false
    val results = mutableListOf<S>()
    var failure: Failure? = null

    do {
        val value = runBlocking { it.next() }
        if (value != null) {
            when (val res = fn(value)) {
                is Either.Right -> results += res.b
                is Either.Left -> failure = res.a
            }
        } else {
            finished = true
        }
    } while (!finished || failure != null)

    return if (failure != null) Either.Left(failure) else Either.Right(results.toList())
}
