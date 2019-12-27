package com.waz.zclient.storage.db.clients.migration

sealed class Option<out T>: Iterable<T> {
    abstract fun isEmpty(): Boolean
    abstract fun <X> map(f: (T) -> X): Option<X>
    abstract fun <X> fold(ifEmpty: () -> X, f: (T) -> X): X

    fun isDefined(): Boolean = !isEmpty()
    fun <T> getOrElse(ifEmpty: () -> T) = fold(ifEmpty){ t -> t }

    data class Some<T>(val value: T): Option<T>() {
        override fun isEmpty(): Boolean = false
        override fun <X> map(f: (T) -> X): Option<X> = Some(f(value))
        override fun iterator(): Iterator<T> = listOf(value).iterator()
        override fun <X> fold(ifEmpty: () -> X, f: (T) -> X): X = f(value)
    }

    object None: Option<Nothing>() {
        override fun isEmpty(): Boolean = true
        override fun iterator(): Iterator<Nothing> = emptyList<Nothing>().iterator()
        override fun <X> map(f: (Nothing) -> X): Option<X> = this
        override fun <X> fold(ifEmpty: () -> X, f: (Nothing) -> X): X = ifEmpty()
    }

    companion object {
        fun <T> from(value: T?): Option<T> = if (value != null) Some(value) else None
    }
}
