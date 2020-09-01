package com.waz.zclient.feature.backup.messages

import com.waz.zclient.core.extension.empty
import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.messages.LikesEntity
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class LikesBackUpModel(
    val messageId: String = String.empty(),
    val userId: String = String.empty(),
    val timeStamp: Long = 0,
    val action: Int = 0
)

class LikesBackupMapper : BackUpDataMapper<LikesBackUpModel, LikesEntity> {
    override fun fromEntity(entity: LikesEntity) = LikesBackUpModel(
        messageId = entity.messageId,
        userId = entity.userId,
        timeStamp = entity.timeStamp,
        action = entity.action
    )

    override fun toEntity(model: LikesBackUpModel) = LikesEntity(
        messageId = model.messageId,
        userId = model.userId,
        timeStamp = model.timeStamp,
        action = model.action
    )
}

class LikesBackupDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<LikesEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<LikesBackUpModel, File>,
    override val mapper: BackUpDataMapper<LikesBackUpModel, LikesEntity>
) : BackUpDataSource<LikesBackUpModel, LikesEntity>()
