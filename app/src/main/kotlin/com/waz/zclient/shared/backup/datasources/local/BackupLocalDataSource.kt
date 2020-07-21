package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.BatchReader
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.list
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

abstract class BackupLocalDataSource<EntityType, JSONType>(
    val name: String,
    protected val dao: BatchReader<EntityType>,
    protected val batchSize: Int,
    private val serializer: KSerializer<JSONType>
) : Iterable<String> {
    protected abstract fun toJSON(entity: EntityType): JSONType
    protected abstract fun toEntity(json: JSONType): EntityType

    fun serialize(entity: EntityType) = json.stringify(serializer, toJSON(entity))

    fun deserialize(jsonStr: String) = toEntity(json.parse(serializer, jsonStr))

    fun serializeList(list: List<EntityType>) =
        json.stringify(serializer.list, list.map { toJSON(it) })

    fun deserializeList(jsonListStr: String) =
        json.parse(serializer.list, jsonListStr).map { toEntity(it) }

    @SuppressWarnings("IteratorNotThrowingNoSuchElementException")
    override fun iterator(): Iterator<String> = object : Iterator<String> {
        private var currentOffset = 0
        private val listSize by lazy { runBlocking { dao.size() } }

        override fun hasNext(): Boolean = currentOffset < listSize

        override fun next(): String {
            val list = runBlocking { dao.getBatch(batchSize, currentOffset) } ?: emptyList()
            currentOffset += list.size
            return serializeList(list)
        }
    }

    companion object {
        protected val json by lazy { Json(JsonConfiguration.Stable.copy(isLenient = true, ignoreUnknownKeys = true)) }

        const val BatchSize: Int = 1000
    }
}
