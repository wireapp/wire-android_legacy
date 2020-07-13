package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.buttons.ButtonDao
import com.waz.zclient.storage.db.buttons.ButtonEntity
import kotlinx.serialization.Serializable

class ButtonLocalDataSource(private val buttonDao: ButtonDao): BackupLocalDataSource<ButtonEntity>() {
    override suspend fun getAll(): List<ButtonEntity> = buttonDao.allButtons()

    override fun serialize(entity: ButtonEntity): String =
        json.stringify(ButtonJSONEntity.serializer(), ButtonJSONEntity.from(entity))
    override fun deserialize(jsonStr: String): ButtonEntity =
        json.parse(ButtonJSONEntity.serializer(), jsonStr).toEntity()
}

@Serializable
data class ButtonJSONEntity(
        val messageId: String = "",
        val buttonId: String = "",
        val title: String = "",
        val ordinal: Int = 0,
        val state: Int = 0
) {
    fun toEntity(): ButtonEntity = ButtonEntity(
        messageId = messageId,
        buttonId = buttonId,
        title = title,
        ordinal = ordinal,
        state = state
    )

    companion object {
        fun from(entity: ButtonEntity): ButtonJSONEntity = ButtonJSONEntity(
            messageId = entity.messageId,
            buttonId = entity.buttonId,
            title = entity.title,
            ordinal = entity.ordinal,
            state = entity.state
        )
    }
}
