package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.core.functional.Utils.returning
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.list
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

abstract class BackupLocalDataSource<EntityType, JSONType>(
    private val serializer: KSerializer<JSONType>,
    private val batchSize: Int = BatchSize
) {
    protected val json by lazy { Json(JsonConfiguration.Stable.copy(isLenient = true, ignoreUnknownKeys = true)) }

    private var currentOffset: Int = 0

    protected abstract suspend fun getInBatch(batchSize: Int, offset: Int): List<EntityType>
    protected abstract fun toJSONType(entity: EntityType): JSONType
    protected abstract fun toEntityType(json: JSONType): EntityType

    fun serialize(entity: EntityType): String = json.stringify(serializer, toJSONType(entity))
    fun deserialize(jsonStr: String): EntityType = toEntityType(json.parse(serializer, jsonStr))
    fun serializeList(list: List<EntityType>): String =
        json.stringify(serializer.list, list.map { entity -> toJSONType(entity) } )
    fun deserializeList(jsonListStr: String): List<EntityType> =
        json.parse(serializer.list, jsonListStr).map { entity -> toEntityType(entity) }

    suspend fun next(): List<EntityType> = returning(getInBatch(batchSize, currentOffset)) {
        currentOffset += it.size
    }

    suspend fun nextJSONString(): String? {
        val list = next()
        return if (list.isNotEmpty()) serializeList(list) else null
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
