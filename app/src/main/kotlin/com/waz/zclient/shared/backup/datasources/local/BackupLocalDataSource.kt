package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.core.functional.Utils.returning
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

abstract class BackupLocalDataSource<T>(val batchSize: Int = BatchSize) {
    protected val json by lazy { Json(JsonConfiguration.Stable.copy(isLenient = true, ignoreUnknownKeys = true)) }

    private var currentOffset: Int = 0

    abstract suspend fun getAll(): List<T>
    protected abstract suspend fun getInBatch(batchSize: Int, offset: Int): List<T>

    abstract fun serialize(entity: T): String
    abstract fun deserialize(jsonStr: String): T

    suspend fun next(): List<T> = returning(getInBatch(batchSize, currentOffset)) {
        currentOffset += this.batchSize
    }

    fun reset(): Unit {
        currentOffset = 0
    }

    companion object {
        fun toIntArray(bytes: ByteArray?): IntArray? = bytes?.map { it.toInt() }?.toIntArray()
        fun toByteArray(ints: IntArray?): ByteArray? = ints?.map { it.toByte() }?.toByteArray()

        const val BatchSize: Int = 1000
    }
}
