package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.receipts.ReadReceiptsDao
import com.waz.zclient.storage.db.receipts.ReadReceiptsEntity
import kotlinx.serialization.Serializable

class ReadReceiptsLocalDataSource(dao: ReadReceiptsDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<ReadReceiptsEntity, ReadReceiptsJSONEntity>("readReceipts", dao, batchSize, ReadReceiptsJSONEntity.serializer()) {
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
