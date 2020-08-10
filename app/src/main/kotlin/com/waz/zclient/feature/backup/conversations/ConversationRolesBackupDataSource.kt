package com.waz.zclient.feature.backup.conversations

import com.waz.zclient.core.extension.empty
import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.conversations.ConversationRoleActionEntity
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class ConversationRoleActionBackUpModel(
    val label: String = String.empty(),
    val action: String = String.empty(),
    val convId: String = String.empty()
)

class ConversationRoleBackupMapper : BackUpDataMapper<ConversationRoleActionBackUpModel, ConversationRoleActionEntity> {
    override fun fromEntity(entity: ConversationRoleActionEntity) =
        ConversationRoleActionBackUpModel(label = entity.label, action = entity.action, convId = entity.convId)

    override fun toEntity(model: ConversationRoleActionBackUpModel) =
        ConversationRoleActionEntity(label = model.label, action = model.action, convId = model.convId)
}

class ConversationRolesBackupDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<ConversationRoleActionEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<ConversationRoleActionBackUpModel, File>,
    override val mapper: BackUpDataMapper<ConversationRoleActionBackUpModel, ConversationRoleActionEntity>
) : BackUpDataSource<ConversationRoleActionBackUpModel, ConversationRoleActionEntity>()
