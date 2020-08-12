package com.waz.zclient.feature.backup.conversations

import com.waz.zclient.core.extension.empty
import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.conversations.ConversationMembersEntity
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class ConversationMembersBackUpModel(
    val userId: String = String.empty(),
    val conversationId: String = String.empty(),
    val role: String = String.empty()
)

class ConversationMembersBackupMapper : BackUpDataMapper<ConversationMembersBackUpModel, ConversationMembersEntity> {
    override fun fromEntity(entity: ConversationMembersEntity) = ConversationMembersBackUpModel(
        userId = entity.userId,
        conversationId = entity.conversationId,
        role = entity.role
    )

    override fun toEntity(model: ConversationMembersBackUpModel) = ConversationMembersEntity(
        userId = model.userId,
        conversationId = model.conversationId,
        role = model.role
    )
}

class ConversationMembersBackupDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<ConversationMembersEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<ConversationMembersBackUpModel, File>,
    override val mapper: BackUpDataMapper<ConversationMembersBackUpModel, ConversationMembersEntity>
) : BackUpDataSource<ConversationMembersBackUpModel, ConversationMembersEntity>()
