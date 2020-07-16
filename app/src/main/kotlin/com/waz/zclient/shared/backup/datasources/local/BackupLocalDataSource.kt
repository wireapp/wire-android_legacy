package com.waz.zclient.shared.backup.datasources.local

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
    protected abstract fun toJSON(entity: EntityType): JSONType
    protected abstract fun toEntity(json: JSONType): EntityType

    fun serialize(entity: EntityType): String = json.stringify(serializer, toJSON(entity))

    fun deserialize(jsonStr: String): EntityType = toEntity(json.parse(serializer, jsonStr))

    fun serializeList(list: List<EntityType>): String =
        json.stringify(serializer.list, list.map { entity -> toJSON(entity) })

    fun deserializeList(jsonListStr: String): List<EntityType> =
        json.parse(serializer.list, jsonListStr).map { jsonEntity -> toEntity(jsonEntity) }

    fun resetCurrentOffset() {
        currentOffset = 0
    }

    fun getCurrentOffset(): Int = currentOffset

    suspend fun nextJSONArrayAsString(): String? {
        val list = getInBatch(batchSize, currentOffset)
        return if (list.isEmpty()) {
            null
        } else {
            currentOffset += list.size
            serializeList(list)
        }
    }

    companion object {
        fun toIntArray(bytes: ByteArray?): IntArray? = bytes?.map { it.toInt() }?.toIntArray()
        fun toByteArray(ints: IntArray?): ByteArray? = ints?.map { it.toByte() }?.toByteArray()

        const val BatchSize: Int = 1000
    }
}
