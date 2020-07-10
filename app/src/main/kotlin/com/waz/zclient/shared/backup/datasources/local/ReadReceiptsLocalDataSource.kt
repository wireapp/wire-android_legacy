package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.receipts.ReadReceiptsDao
import com.waz.zclient.storage.db.receipts.ReadReceiptsEntity
import kotlinx.serialization.Serializable

class ReadReceiptsLocalDataSource(private val readReceiptsDao: ReadReceiptsDao) {
    suspend fun getAllReadReceipts(): List<ReadReceiptsEntity> = readReceiptsDao.allReceipts()
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