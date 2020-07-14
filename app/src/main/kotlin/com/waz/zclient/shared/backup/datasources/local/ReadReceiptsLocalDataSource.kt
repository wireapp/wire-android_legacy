package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.receipts.ReadReceiptsDao
import com.waz.zclient.storage.db.receipts.ReadReceiptsEntity
import kotlinx.serialization.Serializable

class ReadReceiptsLocalDataSource(private val readReceiptsDao: ReadReceiptsDao): BackupLocalDataSource<ReadReceiptsEntity>() {
    override suspend fun getAll(): List<ReadReceiptsEntity> = readReceiptsDao.allReceipts()
    override suspend fun getInBatch(batchSize: Int, offset: Int): List<ReadReceiptsEntity> =
        readReceiptsDao.getReadReceiptsInBatch(batchSize, offset)

    override fun serialize(entity: ReadReceiptsEntity): String =
        json.stringify(ReadReceiptsJSONEntity.serializer(), ReadReceiptsJSONEntity.from(entity))
    override fun deserialize(jsonStr: String): ReadReceiptsEntity =
        json.parse(ReadReceiptsJSONEntity.serializer(), jsonStr).toEntity()
}

@Serializable
data class ReadReceiptsJSONEntity(
    val messageId: String = "",
    val userId: String = "",
    val timestamp: Int = 0
) {
    fun toEntity(): ReadReceiptsEntity = ReadReceiptsEntity(
        messageId = messageId,
        userId = userId,
        timestamp = timestamp
    )

    companion object {
        fun from(entity: ReadReceiptsEntity): ReadReceiptsJSONEntity = ReadReceiptsJSONEntity(
            messageId = entity.messageId,
            userId = entity.userId,
            timestamp = entity.timestamp
        )
    }
}
