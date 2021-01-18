package com.waz.zclient.feature.backup.messages

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.data.messages.MessagesTestDataProvider
import com.waz.zclient.storage.db.messages.MessagesEntity
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class MessagesBackUpDataMapperTest : UnitTest() {

    private lateinit var mapper: MessagesBackUpDataMapper

    @Before
    fun setup() {
        mapper = MessagesBackUpDataMapper()
    }

    @Test
    fun `given a MessagesEntity, when fromEntity() is called, then maps it into a MessagesBackUpModel`() {
        val data = MessagesTestDataProvider.provideDummyTestData()
        val entity = MessagesEntity(
            id = data.id,
            conversationId = data.conversationId,
            messageType = data.messageType,
            userId = data.userId,
            clientId = null,
            errorCode = null,
            content = data.content,
            protos = data.protos,
            time = data.time,
            firstMessage = data.firstMessage,
            members = data.members,
            recipient = data.recipient,
            email = data.email,
            name = data.name,
            messageState = data.messageState,
            contentSize = data.contentSize,
            localTime = data.localTime,
            editTime = data.editTime,
            ephemeral = data.ephemeral,
            expiryTime = data.expiryTime,
            expired = data.expired,
            duration = data.duration,
            quote = data.quote,
            quoteValidity = data.quoteValidity,
            forceReadReceipts = data.forceReadReceipts,
            assetId = data.assetId
        )

        val model = mapper.fromEntity(entity)

        assertEquals(data.id, model.id)
        assertEquals(data.conversationId, model.conversationId)
        assertEquals(data.messageType, model.messageType)
        assertEquals(data.userId, model.userId)
        assertEquals(null, model.clientId)
        assertEquals(null, model.errorCode)
        assertEquals(data.content, model.content)
        assertEquals(data.protos, model.protos)
        assertEquals(data.time, model.time)
        assertEquals(data.firstMessage, model.firstMessage)
        assertEquals(data.members, model.members)
        assertEquals(data.recipient, model.recipient)
        assertEquals(data.email, model.email)
        assertEquals(data.name, model.name)
        assertEquals(data.messageState, model.messageState)
        assertEquals(data.contentSize, model.contentSize)
        assertEquals(data.localTime, model.localTime)
        assertEquals(data.editTime, model.editTime)
        assertEquals(data.ephemeral, model.ephemeral)
        assertEquals(data.expiryTime, model.expiryTime)
        assertEquals(data.duration, model.duration)
        assertEquals(data.quote, model.quote)
        assertEquals(data.quoteValidity, model.quoteValidity)
        assertEquals(data.forceReadReceipts, model.forceReadReceipts)
        assertEquals(data.assetId, model.assetId)
    }

    @Test
    fun `given a MessagesBackUpModel, when toEntity() is called, then maps it into a MessagesEntity`() {
        val data = MessagesTestDataProvider.provideDummyTestData()
        val model = MessagesBackUpModel(
            id = data.id,
            conversationId = data.conversationId,
            messageType = data.messageType,
            userId = data.userId,
            clientId = null,
            errorCode = null,
            content = data.content,
            protos = data.protos,
            time = data.time,
            firstMessage = data.firstMessage,
            members = data.members,
            recipient = data.recipient,
            email = data.email,
            name = data.name,
            messageState = data.messageState,
            contentSize = data.contentSize,
            localTime = data.localTime,
            editTime = data.editTime,
            ephemeral = data.ephemeral,
            expiryTime = data.expiryTime,
            expired = data.expired,
            duration = data.duration,
            quote = data.quote,
            quoteValidity = data.quoteValidity,
            forceReadReceipts = data.forceReadReceipts,
            assetId = data.assetId
        )

        val entity = mapper.toEntity(model)

        assertEquals(data.id, entity.id)
        assertEquals(data.conversationId, entity.conversationId)
        assertEquals(data.messageType, entity.messageType)
        assertEquals(data.userId, entity.userId)
        assertEquals(null, model.clientId)
        assertEquals(null, model.errorCode)
        assertEquals(data.content, entity.content)
        assertEquals(data.protos, entity.protos)
        assertEquals(data.time, entity.time)
        assertEquals(data.firstMessage, entity.firstMessage)
        assertEquals(data.members, entity.members)
        assertEquals(data.recipient, entity.recipient)
        assertEquals(data.email, entity.email)
        assertEquals(data.name, entity.name)
        assertEquals(data.messageState, entity.messageState)
        assertEquals(data.contentSize, entity.contentSize)
        assertEquals(data.localTime, entity.localTime)
        assertEquals(data.editTime, entity.editTime)
        assertEquals(data.ephemeral, entity.ephemeral)
        assertEquals(data.expiryTime, entity.expiryTime)
        assertEquals(data.duration, entity.duration)
        assertEquals(data.quote, entity.quote)
        assertEquals(data.quoteValidity, entity.quoteValidity)
        assertEquals(data.forceReadReceipts, entity.forceReadReceipts)
        assertEquals(data.assetId, entity.assetId)
    }
}
