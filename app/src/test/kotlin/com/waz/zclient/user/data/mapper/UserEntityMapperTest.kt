package com.waz.zclient.user.data.mapper

import com.waz.zclient.userEntity
import junit.framework.TestCase.assertEquals
import org.amshove.kluent.shouldEqualTo

import org.junit.Test

class UserEntityMapperTest {

    @Test
    fun  `Mapping UserEntity to User should be correct`() {
        val user = userEntity.toUser()
        user.id shouldEqualTo userEntity.id
        user.teamId shouldEqualTo userEntity.teamId
        user.name shouldEqualTo userEntity.name
        user.handle shouldEqualTo userEntity.handle
        user.email shouldEqualTo userEntity.email
        user.phone shouldEqualTo userEntity.phone
        user.trackingId shouldEqualTo userEntity.trackingId
        user.picture shouldEqualTo userEntity.picture
        user.pictures shouldEqualTo userEntity.pictures
        user.accentId shouldEqualTo userEntity.accentId
        user.sKey shouldEqualTo userEntity.sKey
        user.accentId shouldEqualTo userEntity.accentId
        user.connection shouldEqualTo userEntity.connection
        user.connectionTimestamp shouldEqualTo userEntity.connectionTimestamp
        user.connectionMessage shouldEqualTo userEntity.connectionMessage
        user.conversation shouldEqualTo userEntity.conversation
        user.relation shouldEqualTo userEntity.relation
        user.timestamp shouldEqualTo userEntity.timestamp
        user.displayName shouldEqualTo userEntity.displayName
        user.verified shouldEqualTo userEntity.verified
        user.deleted shouldEqualTo userEntity.deleted
        user.availability shouldEqualTo userEntity.availability
        user.providerId shouldEqualTo userEntity.providerId
        user.integrationId shouldEqualTo userEntity.integrationId
        user.expiresAt shouldEqualTo userEntity.expiresAt
        user.managedBy shouldEqualTo userEntity.managedBy
        user.selfPermission shouldEqualTo userEntity.selfPermission
        user.copyPermission shouldEqualTo userEntity.copyPermission
        user.createdBy shouldEqualTo userEntity.createdBy
    }
}
