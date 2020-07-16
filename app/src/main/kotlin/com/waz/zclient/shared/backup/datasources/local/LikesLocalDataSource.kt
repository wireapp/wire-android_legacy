package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.messages.LikesDao
import com.waz.zclient.storage.db.messages.LikesEntity
import kotlinx.serialization.Serializable

class LikesLocalDataSource(private val likesDao: LikesDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<LikesEntity, LikesJSONEntity>(LikesJSONEntity.serializer(), batchSize) {
    override suspend fun getInBatch(batchSize: Int, offset: Int): List<LikesEntity> =
        likesDao.getLikesInBatch(batchSize, offset)

    override fun toJSON(entity: LikesEntity): LikesJSONEntity = LikesJSONEntity.from(entity)
    override fun toEntity(json: LikesJSONEntity): LikesEntity = json.toEntity()
}

@Serializable
data class LikesJSONEntity(val messageId: String = "", val userId: String = "", val timeStamp: Int = 0, val action: Int = 0) {
    fun toEntity(): LikesEntity = LikesEntity(
        messageId = messageId,
        userId = userId,
        timeStamp = timeStamp,
        action = action
    )

    companion object {
        fun from(entity: LikesEntity): LikesJSONEntity = LikesJSONEntity(
            messageId = entity.messageId,
            userId = entity.userId,
            timeStamp = entity.timeStamp,
            action = entity.action
        )
    }
}
