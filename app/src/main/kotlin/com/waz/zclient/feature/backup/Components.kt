package com.waz.zclient.feature.backup

interface BackUpIOHandler<T> {
    fun write(iterator: Iterator<T>)
    fun readIterator(): Iterator<T>
}

interface BackUpDataMapper<T, E> {
    fun fromEntity(entity: E): T
    fun toEntity(model: T): E
}
