package com.waz.zclient.feature.backup.buttons

import com.waz.zclient.core.extension.empty
import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.BackUpDataSource
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.storage.db.buttons.ButtonsEntity
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class ButtonsBackUpModel(
    val messageId: String = String.empty(),
    val buttonId: String = String.empty(),
    val title: String = String.empty(),
    val ordinal: Int = 0,
    val state: Int = 0
)

class ButtonsBackupMapper : BackUpDataMapper<ButtonsBackUpModel, ButtonsEntity> {
    override fun fromEntity(entity: ButtonsEntity) = ButtonsBackUpModel(
        messageId = entity.messageId,
        buttonId = entity.buttonId,
        title = entity.title,
        ordinal = entity.ordinal,
        state = entity.state
    )

    override fun toEntity(model: ButtonsBackUpModel) = ButtonsEntity(
            messageId = model.messageId,
            buttonId = model.buttonId,
            title = model.title,
            ordinal = model.ordinal,
            state = model.state
    )
}

class ButtonsBackupDataSource(
    override val databaseLocalDataSource: BackUpIOHandler<ButtonsEntity, Unit>,
    override val backUpLocalDataSource: BackUpIOHandler<ButtonsBackUpModel, File>,
    override val mapper: BackUpDataMapper<ButtonsBackUpModel, ButtonsEntity>
) : BackUpDataSource<ButtonsBackUpModel, ButtonsEntity>()
