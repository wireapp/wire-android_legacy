package com.waz.zclient.user.data.mapper

import com.waz.zclient.userEntity
import junit.framework.TestCase.assertEquals
import org.amshove.kluent.shouldEqualTo

import org.junit.Test

class UserEntityMapperTest {

    @Test
    fun  `Mapping UserEntity to User should be correct`() {
        val user = userEntity.toUser()
        assertEquals(userEntity.id, user.id)
        assertEquals(userEntity.teamId, user.teamId)
        assertEquals(userEntity.name, user.name)
        assertEquals(userEntity.handle, user.handle)
        assertEquals(userEntity.email, user.email)
        assertEquals(userEntity.phone, user.phone)
        assertEquals(userEntity.trackingId, user.trackingId)
        assertEquals(userEntity.picture, user.picture)
        assertEquals(userEntity.pictures, user.pictures)
        assertEquals(userEntity.accentId, user.accentId)
        assertEquals(userEntity.sKey, user.sKey)
        assertEquals(userEntity.accentId, user.accentId)
        assertEquals(userEntity.connection, user.connection)
        assertEquals(userEntity.connectionTimestamp, user.connectionTimestamp)
        assertEquals(userEntity.connectionMessage, user.connectionMessage)
        assertEquals(userEntity.connectionTimestamp, user.connectionTimestamp)
        assertEquals(userEntity.conversation, user.conversation)
        assertEquals(userEntity.relation, user.relation)
        assertEquals(userEntity.timestamp, user.timestamp)
        assertEquals(userEntity.displayName, user.displayName)
        assertEquals(userEntity.verified, user.verified)
        assertEquals(userEntity.deleted, user.deleted)
        assertEquals(userEntity.availability, user.availability)
        assertEquals(userEntity.providerId, user.providerId)
        assertEquals(userEntity.integrationId, user.integrationId)
        assertEquals(userEntity.expiresAt, user.expiresAt)
        assertEquals(userEntity.managedBy, user.managedBy)
        assertEquals(userEntity.selfPermission, user.selfPermission)
        assertEquals(userEntity.copyPermission, user.copyPermission)
        assertEquals(userEntity.createdBy, user.createdBy)
    }
}
