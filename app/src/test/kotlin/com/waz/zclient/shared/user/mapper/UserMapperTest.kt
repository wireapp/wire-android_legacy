package com.waz.zclient.shared.user.mapper

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.user.User
import com.waz.zclient.shared.user.datasources.remote.UserResponse
import com.waz.zclient.storage.db.users.model.UserEntity
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class UserMapperTest : UnitTest() {

    private val userMapper = UserMapper()

    @Mock
    private lateinit var userResponse: UserResponse

    @Mock
    private lateinit var userEntity: UserEntity

    @Mock
    private lateinit var user: User

    @Test
    fun `Given a UserResponse object, when toUser() is called, then it should be mapped to User object`() {

        `when`(userResponse.email).thenReturn(TEST_EMAIL)
        `when`(userResponse.phone).thenReturn(TEST_PHONE)
        `when`(userResponse.handle).thenReturn(TEST_HANDLE)
        `when`(userResponse.locale).thenReturn(TEST_LOCALE)
        `when`(userResponse.managedBy).thenReturn(TEST_MANAGED_BY)
        `when`(userResponse.accentId).thenReturn(TEST_ACCENT)
        `when`(userResponse.name).thenReturn(TEST_NAME)
        `when`(userResponse.id).thenReturn(TEST_ID)

        val user = userMapper.toUser(userResponse)

        assertEquals(TEST_EMAIL, user.email)
        assertEquals(TEST_PHONE, user.phone)
        assertEquals(TEST_HANDLE, user.handle)
        assertEquals(TEST_LOCALE, user.locale)
        assertEquals(TEST_MANAGED_BY, user.managedBy)
        assertEquals(TEST_ACCENT, user.accentId)
        assertEquals(TEST_NAME, user.name)
        assertEquals(TEST_ID, user.id)

    }


    @Test
    fun `Given a UserEntity object, when toUser() is called, then it should be mapped to User object`() {

        `when`(userEntity.id).thenReturn(TEST_ID)
        `when`(userEntity.teamId).thenReturn(TEST_TEAM_ID)
        `when`(userEntity.name).thenReturn(TEST_NAME)
        `when`(userEntity.email).thenReturn(TEST_EMAIL)
        `when`(userEntity.phone).thenReturn(TEST_PHONE)
        `when`(userEntity.trackingId).thenReturn(TEST_TRACKING_ID)
        `when`(userEntity.picture).thenReturn(TEST_PICTURE)
        `when`(userEntity.accentId).thenReturn(TEST_ACCENT)
        `when`(userEntity.sKey).thenReturn(TEST_SKEY)
        `when`(userEntity.connection).thenReturn(TEST_CONNECTION)
        `when`(userEntity.connectionTimestamp).thenReturn(TEST_CONNECTION_TIMESTAMP)
        `when`(userEntity.connectionMessage).thenReturn(TEST_CONNECTION_MESSAGE)
        `when`(userEntity.conversation).thenReturn(TEST_CONVERSATION)
        `when`(userEntity.relation).thenReturn(TEST_RELATION)
        `when`(userEntity.timestamp).thenReturn(TEST_TIMESTAMP)
        `when`(userEntity.verified).thenReturn(TEST_VERIFIED)
        `when`(userEntity.deleted).thenReturn(TEST_DELETED)
        `when`(userEntity.availability).thenReturn(TEST_AVAILABILITY)
        `when`(userEntity.handle).thenReturn(TEST_HANDLE)
        `when`(userEntity.providerId).thenReturn(TEST_PROVIDER_ID)
        `when`(userEntity.integrationId).thenReturn(TEST_INTEGRATION_ID)
        `when`(userEntity.expiresAt).thenReturn(TEST_EXPIRE_AT)
        `when`(userEntity.managedBy).thenReturn(TEST_MANAGED_BY)
        `when`(userEntity.selfPermission).thenReturn(TEST_SELF_PERMISSION)
        `when`(userEntity.copyPermission).thenReturn(TEST_COPY_PERMISSION)
        `when`(userEntity.createdBy).thenReturn(TEST_CREATED_BY)

        val user = userMapper.toUser(userEntity)

        assertEquals(TEST_ID, user.id)
        assertEquals(TEST_TEAM_ID, user.teamId)
        assertEquals(TEST_NAME, user.name)
        assertEquals(TEST_EMAIL, user.email)
        assertEquals(TEST_PHONE, user.phone)
        assertEquals(TEST_TRACKING_ID, user.trackingId)
        assertEquals(TEST_ACCENT, user.accentId)
        assertEquals(TEST_SKEY, user.sKey)
        assertEquals(TEST_CONNECTION, user.connection)
        assertEquals(TEST_CONNECTION_TIMESTAMP, user.connectionTimestamp)
        assertEquals(TEST_CONNECTION_MESSAGE, user.connectionMessage)
        assertEquals(TEST_CONVERSATION, user.conversation)
        assertEquals(TEST_RELATION, user.relation)
        assertEquals(TEST_TIMESTAMP, user.timestamp)
        assertEquals(TEST_VERIFIED, user.verified)
        assertEquals(TEST_DELETED, user.deleted)
        assertEquals(TEST_AVAILABILITY, user.availability)
        assertEquals(TEST_HANDLE, user.handle)
        assertEquals(TEST_PROVIDER_ID, user.providerId)
        assertEquals(TEST_INTEGRATION_ID, user.integrationId)
        assertEquals(TEST_EXPIRE_AT, user.expiresAt)
        assertEquals(TEST_MANAGED_BY, user.managedBy)
        assertEquals(TEST_SELF_PERMISSION, user.selfPermission)
        assertEquals(TEST_COPY_PERMISSION, user.copyPermission)
        assertEquals(TEST_CREATED_BY, user.createdBy)
    }

    @Test
    fun `Given a User object, when toUserEntity() is called, then it should be mapped to UserDao object`() {


        `when`(user.id).thenReturn(TEST_ID)
        `when`(user.teamId).thenReturn(TEST_TEAM_ID)
        `when`(user.name).thenReturn(TEST_NAME)
        `when`(user.email).thenReturn(TEST_EMAIL)
        `when`(user.phone).thenReturn(TEST_PHONE)
        `when`(user.trackingId).thenReturn(TEST_TRACKING_ID)
        `when`(user.picture).thenReturn(TEST_PICTURE)
        `when`(user.accentId).thenReturn(TEST_ACCENT)
        `when`(user.sKey).thenReturn(TEST_SKEY)
        `when`(user.connection).thenReturn(TEST_CONNECTION)
        `when`(user.connectionTimestamp).thenReturn(TEST_CONNECTION_TIMESTAMP)
        `when`(user.connectionMessage).thenReturn(TEST_CONNECTION_MESSAGE)
        `when`(user.conversation).thenReturn(TEST_CONVERSATION)
        `when`(user.relation).thenReturn(TEST_RELATION)
        `when`(user.timestamp).thenReturn(TEST_TIMESTAMP)
        `when`(user.verified).thenReturn(TEST_VERIFIED)
        `when`(user.deleted).thenReturn(TEST_DELETED)
        `when`(user.availability).thenReturn(TEST_AVAILABILITY)
        `when`(user.handle).thenReturn(TEST_HANDLE)
        `when`(user.providerId).thenReturn(TEST_PROVIDER_ID)
        `when`(user.integrationId).thenReturn(TEST_INTEGRATION_ID)
        `when`(user.expiresAt).thenReturn(TEST_EXPIRE_AT)
        `when`(user.managedBy).thenReturn(TEST_MANAGED_BY)
        `when`(user.selfPermission).thenReturn(TEST_SELF_PERMISSION)
        `when`(user.copyPermission).thenReturn(TEST_COPY_PERMISSION)
        `when`(user.createdBy).thenReturn(TEST_CREATED_BY)

        val userEntity = userMapper.toUserEntity(user)

        assertEquals(TEST_ID, userEntity.id)
        assertEquals(TEST_TEAM_ID, userEntity.teamId)
        assertEquals(TEST_NAME, userEntity.name)
        assertEquals(TEST_EMAIL, userEntity.email)
        assertEquals(TEST_PHONE, userEntity.phone)
        assertEquals(TEST_TRACKING_ID, userEntity.trackingId)
        assertEquals(TEST_PICTURE, userEntity.picture)
        assertEquals(TEST_ACCENT, userEntity.accentId)
        assertEquals(TEST_SKEY, userEntity.sKey)
        assertEquals(TEST_CONNECTION, userEntity.connection)
        assertEquals(TEST_CONNECTION_TIMESTAMP, userEntity.connectionTimestamp)
        assertEquals(TEST_CONNECTION_MESSAGE, userEntity.connectionMessage)
        assertEquals(TEST_CONVERSATION, userEntity.conversation)
        assertEquals(TEST_RELATION, userEntity.relation)
        assertEquals(TEST_TIMESTAMP, userEntity.timestamp)
        assertEquals(TEST_VERIFIED, userEntity.verified)
        assertEquals(TEST_DELETED, userEntity.deleted)
        assertEquals(TEST_AVAILABILITY, userEntity.availability)
        assertEquals(TEST_HANDLE, userEntity.handle)
        assertEquals(TEST_PROVIDER_ID, userEntity.providerId)
        assertEquals(TEST_INTEGRATION_ID, userEntity.integrationId)
        assertEquals(TEST_EXPIRE_AT, userEntity.expiresAt)
        assertEquals(TEST_MANAGED_BY, userEntity.managedBy)
        assertEquals(TEST_SELF_PERMISSION, userEntity.selfPermission)
        assertEquals(TEST_COPY_PERMISSION, userEntity.copyPermission)
        assertEquals(TEST_CREATED_BY, userEntity.createdBy)
    }

    companion object {
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_PHONE = "+49339939300303939"
        private const val TEST_HANDLE = "handle"
        private const val TEST_LOCALE = "en"
        private const val TEST_MANAGED_BY = "wire"
        private const val TEST_ACCENT = 0
        private const val TEST_NAME = "test"
        private const val TEST_ID = "id"
        private const val TEST_TEAM_ID = "1834848-4848-4884"
        private const val TEST_TRACKING_ID = "55"
        private const val TEST_PICTURE = "339893-33393db-3i3"
        private const val TEST_SKEY = "3@@@44"
        private const val TEST_CONNECTION = "unconnected"
        private const val TEST_CONNECTION_TIMESTAMP = 1584462652
        private const val TEST_CONNECTION_MESSAGE = "No Internet connection!"
        private const val TEST_CONVERSATION = "Hey guys"
        private const val TEST_TIMESTAMP = 1584462652
        private const val TEST_RELATION = "Other"
        private const val TEST_VERIFIED = "UNKNOWN"
        private const val TEST_DELETED = false
        private const val TEST_AVAILABILITY = 0
        private const val TEST_PROVIDER_ID = "50-50-KDK"
        private const val TEST_INTEGRATION_ID = "5050"
        private const val TEST_EXPIRE_AT = 1584462652
        private const val TEST_SELF_PERMISSION = 0
        private const val TEST_COPY_PERMISSION = 0
        private const val TEST_CREATED_BY = "wire"
    }
}
