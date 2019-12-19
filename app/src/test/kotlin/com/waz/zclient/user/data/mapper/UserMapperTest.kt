package com.waz.zclient.user.data.mapper

import com.waz.zclient.user
import com.waz.zclient.userApi
import com.waz.zclient.userDao
import junit.framework.TestCase.assertEquals
import org.junit.Test

class UserMapperTest {

    private val userMapper = UserMapper()

    @Test
    fun `Mapping UserApi to User should be correct`() {

        val user = userMapper.toUser(userApi)

        assertEquals(userApi.id, user.id)
        assertEquals(userApi.name, user.name)
        assertEquals(userApi.handle, user.handle)
        assertEquals(userApi.email, user.email)
        assertEquals(userApi.phone, user.phone)
        assertEquals(userApi.pictures, user.pictures)
        assertEquals(userApi.accentId, user.accentId)
        assertEquals(userApi.accentId, user.accentId)
        assertEquals(userApi.deleted, user.deleted)
        assertEquals(userApi.managedBy, user.managedBy)
    }

    @Test
    fun `Mapping UserDao to User should be correct`() {

        val user = userMapper.toUser(userDao)

        assertEquals(userDao.id, user.id)
        assertEquals(userDao.teamId, user.teamId)
        assertEquals(userDao.name, user.name)
        assertEquals(userDao.handle, user.handle)
        assertEquals(userDao.email, user.email)
        assertEquals(userDao.phone, user.phone)
        assertEquals(userDao.trackingId, user.trackingId)
        assertEquals(userDao.picture, user.picture)
        assertEquals(userDao.accentId, user.accentId)
        assertEquals(userDao.sKey, user.sKey)
        assertEquals(userDao.connection, user.connection)
        assertEquals(userDao.connectionTimestamp, user.connectionTimestamp)
        assertEquals(userDao.connectionMessage, user.connectionMessage)
        assertEquals(userDao.conversation, user.conversation)
        assertEquals(userDao.relation, user.relation)
        assertEquals(userDao.connection, user.connection)
        assertEquals(userDao.timestamp, user.timestamp)
        assertEquals(userDao.displayName, user.displayName)
        assertEquals(userDao.verified, user.verified)
        assertEquals(userDao.deleted, user.deleted)
        assertEquals(userDao.availability, user.availability)
        assertEquals(userDao.providerId, user.providerId)
        assertEquals(userDao.integrationId, user.integrationId)
        assertEquals(userDao.expiresAt, user.expiresAt)
        assertEquals(userDao.managedBy, user.managedBy)
        assertEquals(userDao.selfPermission, user.selfPermission)
        assertEquals(userDao.copyPermission, user.copyPermission)
        assertEquals(userDao.createdBy, user.createdBy)
    }

    @Test
    fun `Mapping User to UserDao should be correct`() {

        val userDao = userMapper.toUserDao(user)

        assertEquals(user.id, userDao.id)
        assertEquals(user.teamId, userDao.teamId)
        assertEquals(user.name, userDao.name)
        assertEquals(user.handle, userDao.handle)
        assertEquals(user.email, userDao.email)
        assertEquals(user.phone, userDao.phone)
        assertEquals(user.trackingId, userDao.trackingId)
        assertEquals(user.picture, userDao.picture)
        assertEquals(user.accentId, userDao.accentId)
        assertEquals(user.sKey, userDao.sKey)
        assertEquals(user.connection, userDao.connection)
        assertEquals(user.connectionTimestamp, userDao.connectionTimestamp)
        assertEquals(user.connectionMessage, userDao.connectionMessage)
        assertEquals(user.conversation, userDao.conversation)
        assertEquals(user.relation, userDao.relation)
        assertEquals(user.connection, userDao.connection)
        assertEquals(user.timestamp, userDao.timestamp)
        assertEquals(user.displayName, userDao.displayName)
        assertEquals(user.verified, userDao.verified)
        assertEquals(user.deleted, userDao.deleted)
        assertEquals(user.availability, userDao.availability)
        assertEquals(user.providerId, userDao.providerId)
        assertEquals(user.integrationId, userDao.integrationId)
        assertEquals(user.expiresAt, userDao.expiresAt)
        assertEquals(user.managedBy, userDao.managedBy)
        assertEquals(user.selfPermission, userDao.selfPermission)
        assertEquals(user.copyPermission, userDao.copyPermission)
        assertEquals(user.createdBy, userDao.createdBy)
    }
}
