package com.waz.zclient.shared.backup.datasources.local

import com.waz.zclient.storage.db.buttons.ButtonDao
import com.waz.zclient.storage.db.buttons.ButtonEntity
import kotlinx.serialization.Serializable

class ButtonLocalDataSource(dao: ButtonDao, batchSize: Int = BatchSize) :
BackupLocalDataSource<ButtonEntity, ButtonJSONEntity>("buttons", dao, batchSize, ButtonJSONEntity.serializer()) {
    override fun toJSON(entity: ButtonEntity) = ButtonJSONEntity.from(entity)
    override fun toEntity(json: ButtonJSONEntity) = json.toEntity()
}

@Serializable
data class ButtonJSONEntity(
    val messageId: String = "",
    val buttonId: String = "",
    val title: String = "",
    val ordinal: Int = 0,
    val state: Int = 0
) {
    fun toEntity() = ButtonEntity(messageId, buttonId, title, ordinal, state)

    companion object {
        fun from(entity: ButtonEntity) = ButtonJSONEntity(entity.messageId, entity.buttonId, entity.title, entity.ordinal, entity.state)
    }
}
