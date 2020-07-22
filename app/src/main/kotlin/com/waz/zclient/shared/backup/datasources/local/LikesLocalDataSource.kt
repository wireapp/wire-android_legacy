package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.messages.LikesDao
import com.waz.zclient.storage.db.messages.LikesEntity
import kotlinx.serialization.Serializable

class LikesLocalDataSource(dao: LikesDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<LikesEntity, LikesJSONEntity>("likes", dao, batchSize, LikesJSONEntity.serializer()) {
    override fun toJSON(entity: LikesEntity): LikesJSONEntity = LikesJSONEntity.from(entity)
    override fun toEntity(json: LikesJSONEntity): LikesEntity = json.toEntity()
}

@Serializable
data class LikesJSONEntity(val messageId: String = "", val userId: String = "", val timeStamp: Int = 0, val action: Int = 0) {
    fun toEntity() = LikesEntity(messageId, userId, timeStamp, action)

    companion object {
        fun from(entity: LikesEntity) = LikesJSONEntity(entity.messageId, entity.userId, entity.timeStamp, entity.action)
    }
}
