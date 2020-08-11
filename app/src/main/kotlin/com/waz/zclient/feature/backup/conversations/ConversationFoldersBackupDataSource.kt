package com.waz.zclient.feature.backup.conversations

import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.conversations.ConversationFoldersEntity
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class ConversationFoldersBackUpModel(
    val convId: String,
    val folderId: String
)

class ConversationFoldersBackupMapper : BackUpDataMapper<ConversationFoldersBackUpModel, ConversationFoldersEntity> {
    override fun fromEntity(entity: ConversationFoldersEntity) = ConversationFoldersBackUpModel(
        entity.convId,
        entity.folderId
    )

    override fun toEntity(model: ConversationFoldersBackUpModel) = ConversationFoldersEntity(
        model.convId,
        model.folderId
    )
}

class ConversationFoldersBackupDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<ConversationFoldersEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<ConversationFoldersBackUpModel, File>,
    override val mapper: BackUpDataMapper<ConversationFoldersBackUpModel, ConversationFoldersEntity>
) : BackUpDataSource<ConversationFoldersBackUpModel, ConversationFoldersEntity>()
