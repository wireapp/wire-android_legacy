package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.messages.LikesDao
import com.waz.zclient.storage.db.messages.LikesEntity
import kotlinx.serialization.Serializable

class LikesLocalDataSource(private val likesDao: LikesDao) {
    suspend fun getAllLikes(): List<LikesEntity> = likesDao.allLikes()
}

@Serializable
data class LikesJSONEntity(
        val messageId: String = "",
        val userId: String = "",
        val timeStamp: Int = 0,
        val action: Int = 0
) {
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