package com.waz.zclient.feature.backup

//TODO: implement BackUpRepository
@Suppress("IteratorNotThrowingNoSuchElementException")
//TODO handle NoSuchElement when signature changes
abstract class BackUpDataSource<T, E> {
    abstract val databaseLocalDataSource: BackUpIOHandler<E>
    abstract val backUpLocalDataSource: BackUpIOHandler<T>
    abstract val mapper: BackUpDataMapper<T, E>

    fun backUp() {
        val readIterator = databaseLocalDataSource.readIterator()
        val writeIterator: Iterator<T> = object : Iterator<T> {
            override fun hasNext(): Boolean = readIterator.hasNext()
            override fun next(): T = mapper.fromEntity(readIterator.next())
        }
        backUpLocalDataSource.write(writeIterator)
    }

    fun restore() {
        val readIterator = backUpLocalDataSource.readIterator()
        val writeIterator: Iterator<E> = object : Iterator<E> {
            override fun hasNext(): Boolean = readIterator.hasNext()
            override fun next(): E = mapper.toEntity(readIterator.next())
        }
        databaseLocalDataSource.write(writeIterator)
    }
}
