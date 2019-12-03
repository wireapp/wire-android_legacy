package com.waz.zclient.core.requests

sealed class Either<out Failure, out Success> {

    data class Left<out L>(val a: L) : Either<L, Nothing>()
    data class Right<out R>(val b: R) : Either<Nothing, R>()

    val isRight get() = this is Right<Success>
    val isLeft get() = this is Left<Failure>

    fun <L> left(a: L) = Left(a)
    fun <R> right(b: R) = Right(b)

    fun either(fnL: (Failure) -> Any, fnR: (Success) -> Any): Any =
        when (this) {
            is Left -> fnL(a)
            is Right -> fnR(b)
        }
}

// Composes 2 functions
fun <A, B, C> ((A) -> B).c(f: (B) -> C): (A) -> C = {
    f(this(it))
}

fun <T, L, R> Either<L, R>.flatMap(fn: (R) -> Either<L, T>): Either<L, T> =
    when (this) {
        is Either.Left -> Either.Left(a)
        is Either.Right -> fn(b)
    }

fun <T, L, R> Either<L, R>.map(fn: (R) -> (T)): Either<L, T> = this.flatMap(fn.c(::right))

data class Failure(val message: String)
