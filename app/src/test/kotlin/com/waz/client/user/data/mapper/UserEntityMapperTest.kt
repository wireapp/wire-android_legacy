package com.waz.client.user.data.mapper

import com.waz.client.userEntity
import com.waz.zclient.user.data.mapper.UserEntityMapper
import junit.framework.TestCase.assertEquals

import org.junit.Test

class UserEntityMapperTest {


    private val userMapper = UserEntityMapper()

    @Test
    fun test_MapToDomain() {

        val user = userMapper.mapToDomain(userEntity)
        assertEquals(userEntity.id, user.id)
        assertEquals(userEntity.email, user.email)
        assertEquals(userEntity.phone, user.phone)
        assertEquals(userEntity.handle, user.handle)
        assertEquals(userEntity.locale, user.locale)
        assertEquals(userEntity.managedBy, user.managedBy)
        assertEquals(userEntity.accentId, user.accentId)
        assertEquals(userEntity.name, user.name)
        assertEquals(userEntity.deleted, user.deleted)
    }
}
