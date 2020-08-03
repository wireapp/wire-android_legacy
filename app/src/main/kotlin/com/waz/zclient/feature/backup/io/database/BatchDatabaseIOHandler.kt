package com.waz.zclient.feature.backup.io.database

import com.waz.zclient.feature.backup.BackUpIOHandler

class BatchDatabaseIOHandler<E>(private val batchReadableDao: BatchReadableDao<E>, private val batchSize: Int) : BackUpIOHandler<E> {

    override fun write(iterator: Iterator<E>) {
        while (iterator.hasNext()) {
            batchReadableDao.insert(iterator.next())
        }
    }

    override fun readIterator(): Iterator<E> = object : Iterator<E> {
        var count = 0
        val currentBatch = mutableListOf<E>()

        override fun hasNext(): Boolean = count < batchReadableDao.count()

        //TODO map NoSuchElementException to Either domain.
        override fun next(): E =
            if (!hasNext()) throw NoSuchElementException()
            else {
                if (count % batchSize == 0) {
                    currentBatch.clear()
                    currentBatch.addAll(batchReadableDao.getNextBatch(
                        start = count,
                        batchSize = (batchReadableDao.count() - count).coerceAtMost(batchSize))
                    )
                }
                currentBatch[count % batchSize].also {
                    count++
                }
            }
    }
}

interface BatchReadableDao<E> {
    fun count(): Int

    fun getNextBatch(start: Int, batchSize: Int): List<E>

    fun insert(item: E)
}
