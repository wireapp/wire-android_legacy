package com.waz.zclient.feature.backup.users

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.data.users.UsersTestDataProvider
import com.waz.zclient.storage.db.users.model.UsersEntity
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class UsersBackUpDataMapperTest : UnitTest() {

    private lateinit var mapper: UsersBackUpDataMapper

    @Before
    fun setup() {
        mapper = UsersBackUpDataMapper()
    }

    @Test
    fun `given a UsersEntity, when fromEntity() is called, then maps it into a UsersBackUpModel`() {
        val data = UsersTestDataProvider.provideDummyTestData()
        val entity = UsersEntity(
            id = data.id,
            domain = data.domain,
            teamId = data.teamId,
            name = data.name,
            email = data.email,
            phone = data.phone,
            trackingId = data.trackingId,
            picture = data.picture,
            accentId = data.accentId,
            sKey = data.sKey,
            connection = data.connection,
            connectionTimestamp = data.connectionTimestamp,
            connectionMessage = data.connectionMessage,
            conversation = data.conversation,
            conversationDomain = data.conversationDomain,
            relation = data.relation,
            timestamp = data.timestamp,
            verified = data.verified,
            deleted = data.deleted,
            availability = data.availability,
            handle = data.handle,
            providerId = data.providerId,
            integrationId = data.integrationId,
            expiresAt = data.expiresAt,
            managedBy = data.managedBy,
            selfPermission = data.selfPermission,
            copyPermission = data.copyPermission,
            createdBy = data.createdBy
        )

        val model = mapper.fromEntity(entity)
        assertEquals(data.id, model.id)
        assertEquals(data.teamId, model.teamId)
        assertEquals(data.name, model.name)
        assertEquals(data.email, model.email)
        assertEquals(data.phone, model.phone)
        assertEquals(data.trackingId, model.trackingId)
        assertEquals(data.picture, model.picture)
        assertEquals(data.accentId, model.accentId)
        assertEquals(data.sKey, model.sKey)
        assertEquals(data.connection, model.connection)
        assertEquals(data.connectionTimestamp, model.connectionTimestamp)
        assertEquals(data.connectionMessage, model.connectionMessage)
        assertEquals(data.conversation, model.conversation)
        assertEquals(data.conversationDomain, model.conversationDomain)
        assertEquals(data.relation, model.relation)
        assertEquals(data.timestamp, model.timestamp)
        assertEquals(data.verified, model.verified)
        assertEquals(data.deleted, model.deleted)
        assertEquals(data.availability, model.availability)
        assertEquals(data.handle, model.handle)
        assertEquals(data.providerId, model.providerId)
        assertEquals(data.integrationId, model.integrationId)
        assertEquals(data.expiresAt, model.expiresAt)
        assertEquals(data.managedBy, model.managedBy)
        assertEquals(data.selfPermission, model.selfPermission)
        assertEquals(data.copyPermission, model.copyPermission)
        assertEquals(data.createdBy, model.createdBy)
    }

    @Test
    fun `given a UsersBackUpModel, when toEntity() is called, then maps it into a UsersEntity`() {
        val data = UsersTestDataProvider.provideDummyTestData()
        val model = UsersBackUpModel(
            id = data.id,
            domain = data.domain,
            teamId = data.teamId,
            name = data.name,
            email = data.email,
            phone = data.phone,
            trackingId = data.trackingId,
            picture = data.picture,
            accentId = data.accentId,
            sKey = data.sKey,
            connection = data.connection,
            connectionTimestamp = data.connectionTimestamp,
            connectionMessage = data.connectionMessage,
            conversation = data.conversation,
            conversationDomain = data.conversationDomain,
            relation = data.relation,
            timestamp = data.timestamp,
            verified = data.verified,
            deleted = data.deleted,
            availability = data.availability,
            handle = data.handle,
            providerId = data.providerId,
            integrationId = data.integrationId,
            expiresAt = data.expiresAt,
            managedBy = data.managedBy,
            selfPermission = data.selfPermission,
            copyPermission = data.copyPermission,
            createdBy = data.createdBy
        )

        val entity = mapper.toEntity(model)

        assertEquals(data.id, entity.id)
        assertEquals(data.domain, entity.domain)
        assertEquals(data.teamId, entity.teamId)
        assertEquals(data.name, entity.name)
        assertEquals(data.email, entity.email)
        assertEquals(data.phone, entity.phone)
        assertEquals(data.trackingId, entity.trackingId)
        assertEquals(data.picture, entity.picture)
        assertEquals(data.accentId, entity.accentId)
        assertEquals(data.sKey, entity.sKey)
        assertEquals(data.connection, entity.connection)
        assertEquals(data.connectionTimestamp, entity.connectionTimestamp)
        assertEquals(data.connectionMessage, entity.connectionMessage)
        assertEquals(data.conversation, entity.conversation)
        assertEquals(data.conversationDomain, entity.conversationDomain)
        assertEquals(data.relation, entity.relation)
        assertEquals(data.timestamp, entity.timestamp)
        assertEquals(data.verified, entity.verified)
        assertEquals(data.deleted, entity.deleted)
        assertEquals(data.availability, entity.availability)
        assertEquals(data.handle, entity.handle)
        assertEquals(data.providerId, entity.providerId)
        assertEquals(data.integrationId, entity.integrationId)
        assertEquals(data.expiresAt, entity.expiresAt)
        assertEquals(data.managedBy, entity.managedBy)
        assertEquals(data.selfPermission, entity.selfPermission)
        assertEquals(data.copyPermission, entity.copyPermission)
        assertEquals(data.createdBy, entity.createdBy)
    }
}
