package com.waz.zclient.feature.backup.io.database

import com.waz.zclient.feature.backup.BackUpIOHandler

class SingleReadDatabaseIOHandler<T>(private val singleReadDao: SingleReadDao<T>) : BackUpIOHandler<T> {

    override fun write(iterator: Iterator<T>) {
        while (iterator.hasNext()) {
            singleReadDao.insert(iterator.next())
        }
    }

    @Suppress("IteratorNotThrowingNoSuchElementException") //listIterator throws it
    override fun readIterator() = object : Iterator<T> {
        val listIterator by lazy { singleReadDao.getAll().iterator() }

        override fun hasNext(): Boolean = listIterator.hasNext()

        override fun next(): T = listIterator.next()
    }
}

interface SingleReadDao<T> {
    fun insert(item: T)
    fun getAll(): List<T>
}
