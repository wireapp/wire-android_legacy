package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.buttons.ButtonDao
import com.waz.zclient.storage.db.buttons.ButtonEntity
import kotlinx.serialization.Serializable

class ButtonLocalDataSource(private val buttonDao: ButtonDao):
    BackupLocalDataSource<ButtonEntity, ButtonJSONEntity>(ButtonJSONEntity.serializer()) {
    override suspend fun getInBatch(batchSize: Int, offset: Int): List<ButtonEntity> =
        buttonDao.getButtonsInBatch(batchSize, offset)

    override fun toJSONType(entity: ButtonEntity): ButtonJSONEntity = ButtonJSONEntity.from(entity)
    override fun toEntityType(json: ButtonJSONEntity): ButtonEntity = json.toEntity()
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
