package com.waz.zclient.feature.backup.buttons

import com.waz.zclient.core.extension.empty
import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.ButtonEntity
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class ButtonBackUpModel(
    val messageId: String = String.empty(),
    val buttonId: String = String.empty(),
    val title: String = String.empty(),
    val ordinal: Int = 0,
    val state: Int = 0
)

class ButtonBackupMapper : BackUpDataMapper<ButtonBackUpModel, ButtonEntity> {
    override fun fromEntity(entity: ButtonEntity) = ButtonBackUpModel(
        messageId = entity.messageId,
        buttonId = entity.buttonId,
        title = entity.title,
        ordinal = entity.ordinal,
        state = entity.state
    )

    override fun toEntity(model: ButtonBackUpModel) = ButtonEntity(
        messageId = model.messageId,
        buttonId = model.buttonId,
        title = model.title,
        ordinal = model.ordinal,
        state = model.state
    )
}

class ButtonsBackupDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<ButtonEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<ButtonBackUpModel, File>,
    override val mapper: BackUpDataMapper<ButtonBackUpModel, ButtonEntity>
) : BackUpDataSource<ButtonBackUpModel, ButtonEntity>()
