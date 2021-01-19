package com.waz.zclient.feature.backup.messages

import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.storage.db.messages.MessagesEntity

class MessagesBackUpDataMapper : BackUpDataMapper<MessagesBackUpModel, MessagesEntity> {

    override fun fromEntity(entity: MessagesEntity): MessagesBackUpModel = MessagesBackUpModel(
        id = entity.id,
        conversationId = entity.conversationId,
        messageType = entity.messageType,
        userId = entity.userId,
        clientId = entity.clientId,
        errorCode = entity.errorCode,
        content = entity.content,
        protos = entity.protos,
        time = entity.time,
        firstMessage = entity.firstMessage,
        members = entity.members,
        recipient = entity.recipient,
        email = entity.email,
        name = entity.name,
        messageState = entity.messageState,
        contentSize = entity.contentSize,
        localTime = entity.localTime,
        editTime = entity.editTime,
        ephemeral = entity.ephemeral,
        expiryTime = entity.expiryTime,
        expired = entity.expired,
        duration = entity.duration,
        quote = entity.quote,
        quoteValidity = entity.quoteValidity,
        forceReadReceipts = entity.forceReadReceipts,
        assetId = entity.assetId
    )

    override fun toEntity(model: MessagesBackUpModel): MessagesEntity = MessagesEntity(
        id = model.id,
        conversationId = model.conversationId,
        messageType = model.messageType,
        userId = model.userId,
        clientId = model.clientId,
        errorCode = model.errorCode,
        content = model.content,
        protos = model.protos,
        time = model.time,
        firstMessage = model.firstMessage,
        members = model.members,
        recipient = model.recipient,
        email = model.email,
        name = model.name,
        messageState = model.messageState,
        contentSize = model.contentSize,
        localTime = model.localTime,
        editTime = model.editTime,
        ephemeral = model.ephemeral,
        expiryTime = model.expiryTime,
        expired = model.expired,
        duration = model.duration,
        quote = model.quote,
        quoteValidity = model.quoteValidity,
        forceReadReceipts = model.forceReadReceipts,
        assetId = model.assetId
    )
}
