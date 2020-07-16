package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.receipts.ReadReceiptsDao
import com.waz.zclient.storage.db.receipts.ReadReceiptsEntity
import kotlinx.serialization.Serializable

class ReadReceiptsLocalDataSource(private val readReceiptsDao: ReadReceiptsDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<ReadReceiptsEntity, ReadReceiptsJSONEntity>(ReadReceiptsJSONEntity.serializer(), batchSize) {
    override suspend fun getInBatch(batchSize: Int, offset: Int): List<ReadReceiptsEntity> =
        readReceiptsDao.getReadReceiptsInBatch(batchSize, offset)

    override fun toJSON(entity: ReadReceiptsEntity): ReadReceiptsJSONEntity = ReadReceiptsJSONEntity.from(entity)
    override fun toEntity(json: ReadReceiptsJSONEntity): ReadReceiptsEntity = json.toEntity()
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
