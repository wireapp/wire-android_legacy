package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.UserJSONEntity
import com.waz.zclient.shared.backup.datasources.local.UsersLocalDataSource
import com.waz.zclient.storage.db.users.model.UserEntity
import com.waz.zclient.storage.db.users.service.UserDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

@ExperimentalCoroutinesApi
class UserLocalDataSourceTest : UnitTest() {

    private val userEntity = UserEntity(
        id = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        teamId = "3762d820-83a1-4fae-ae58-6c39fb2e9d8a",
        name = "Maciek",
        email = "maciek@wire.com",
        phone = null,
        trackingId = null,
        picture = "2f9e89c9-78a7-477d-8def-fbd7ca3846b5",
        accentId = 0,
        sKey = "",
        connection = "",
        connectionTimestamp = 0,
        connectionMessage = null,
        conversation = null,
        relation = "0",
        timestamp = null,
        verified = null,
        deleted = false,
        availability = 0,
        handle = "@maciek",
        providerId = null,
        integrationId = null,
        expiresAt = null,
        managedBy = null,
        selfPermission = 0,
        copyPermission = 0,
        createdBy = null
    )

    @Mock
    private lateinit var userDao: UserDao
    private lateinit var dataSource: UsersLocalDataSource

    @Before
    fun setup() {
        dataSource = UsersLocalDataSource(userDao)
    }

    @Test
    fun `convert a user entity to a json entity and back`() = run {
        val userJSONEntity = UserJSONEntity.from(userEntity)
        val result: UserEntity = userJSONEntity.toEntity()

        result shouldEqual userEntity
    }

    @Test
    fun `convert a user entity to json string and back`(): Unit = run {
        val jsonStr = dataSource.serialize(userEntity)
        val result = dataSource.deserialize(jsonStr)

        result.id shouldEqual userEntity.id
    }
}