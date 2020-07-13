package com.waz.zclient.shared.backup.datasources.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

abstract class BackupLocalDataSource<T> {
    protected val json by lazy { Json(JsonConfiguration.Stable.copy(isLenient = true, ignoreUnknownKeys = true)) }

    abstract suspend fun getAll(): List<T>
    abstract fun serialize(entity: T): String
    abstract fun deserialize(jsonStr: String): T

    companion object {
        fun toIntArray(bytes: ByteArray?): IntArray? = bytes?.map { it.toInt() }?.toIntArray()
        fun toByteArray(ints: IntArray?): ByteArray? = ints?.map { it.toByte() }?.toByteArray()
    }
}
