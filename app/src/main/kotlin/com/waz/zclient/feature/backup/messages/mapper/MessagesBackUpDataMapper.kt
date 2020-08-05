package com.waz.zclient.feature.backup.messages.mapper

import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.feature.backup.messages.MessagesBackUpModel
import com.waz.zclient.storage.db.messages.MessagesEntity

class MessagesBackUpDataMapper : BackUpDataMapper<MessagesBackUpModel, MessagesEntity> {
    override fun fromEntity(entity: MessagesEntity): MessagesBackUpModel {
        TODO("Not yet implemented")
    }

    override fun toEntity(model: MessagesBackUpModel): MessagesEntity {
        TODO("Not yet implemented")
    }
}
