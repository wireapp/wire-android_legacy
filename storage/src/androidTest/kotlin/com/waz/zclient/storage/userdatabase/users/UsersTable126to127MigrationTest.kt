package com.waz.zclient.storage.userdatabase.users

import com.waz.zclient.storage.DbSQLiteOpenHelper
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.MigrationTestHelper
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.di.StorageModule.getUserDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class UsersTable126to127MigrationTest : IntegrationTest() {

    private lateinit var testOpenHelper: DbSQLiteOpenHelper

    private lateinit var testHelper: MigrationTestHelper

    @Before
    fun setUp() {
        testHelper = MigrationTestHelper(UserDatabase::class.java.canonicalName)
        testOpenHelper = DbSQLiteOpenHelper(getApplicationContext(),
            TEST_DB_NAME,126)
        UsersTableTestHelper.createTable(testOpenHelper)
    }

    @After
    fun tearDown() {
        UsersTableTestHelper.clearTable(testOpenHelper)
        UsersTableTestHelper.closeDatabase(testOpenHelper)
    }

    @Test
    fun givenUserInsertedIntoUsersTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {
        UsersTableTestHelper.insertUser(
            id = TEST_USER_ID,
            teamId = TEST_TEAM_ID,
            name = TEST_NAME,
            email = TEST_EMAIL,
            phone = TEST_PHONE,
            trackingId = TEST_TRACKING_ID,
            picture = TEST_PICTURE,
            accentId = TEST_ACCENT_ID,
            sKey = TEST_SKEY,
            connection = TEST_CONNECTION,
            connectionTimestamp = TEST_CONNECTION_TIMESTAMP,
            connectionMessage = TEST_CONNECTION_MESSAGE,
            conversation = TEST_CONVERSATION,
            relation = TEST_RELATION,
            timestamp = TEST_TIMESTAMP,
            verified = TEST_VERIFIED,
            deleted = TEST_DELETED,
            availability = TEST_AVAILABILITY,
            handle = TEST_HANDLE,
            providerId = TEST_PROVIDER_ID,
            integrationId = TEST_INTEGRATION_ID,
            expiresAt = TEST_EXPIRES_AT,
            managedBy = TEST_MANAGED_BY,
            selfPermission = TEST_SELF_PERMISSIONS,
            copyPermission = TEST_COPY_PERMISSIONS,
            createdBy = TEST_CREATED_BY,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            val user = getAllUsers()[0]
            with(user) {
                assert(id == TEST_USER_ID)
                assert(teamId == TEST_TEAM_ID)
                assert(name == TEST_NAME)
                assert(email == TEST_EMAIL)
                assert(phone == TEST_PHONE)
                assert(trackingId == TEST_TRACKING_ID)
                assert(picture == TEST_PICTURE)
                assert(accentId == TEST_ACCENT_ID)
                assert(sKey == TEST_SKEY)
                assert(connection == TEST_CONNECTION)
                assert(connectionTimestamp == TEST_CONNECTION_TIMESTAMP)
                assert(connectionMessage == TEST_CONNECTION_MESSAGE)
                assert(conversation == TEST_CONVERSATION)
                assert(relation == TEST_RELATION)
                assert(timestamp == TEST_TIMESTAMP)
                assert(verified == TEST_VERIFIED)
                assert(deleted == TEST_DELETED)
                assert(availability == TEST_AVAILABILITY)
                assert(handle == TEST_HANDLE)
                assert(providerId == TEST_PROVIDER_ID)
                assert(integrationId == TEST_INTEGRATION_ID)
                assert(expiresAt == TEST_EXPIRES_AT)
                assert(managedBy == TEST_MANAGED_BY)
                assert(selfPermission == TEST_SELF_PERMISSIONS)
                assert(copyPermission == TEST_COPY_PERMISSIONS)
                assert(createdBy == TEST_CREATED_BY)
            }
        }
    }

    private fun validateMigration() =
        testHelper.validateMigration(
            TEST_DB_NAME,
            127,
            true,
            USER_DATABASE_MIGRATION_126_TO_127
        )

    private fun getUserDb() =
        getUserDatabase(
            getApplicationContext(),
            TEST_DB_NAME,
            UserDatabase.migrations
        )

    private suspend fun getAllUsers() =
        getUserDb().userDbService().allUsers()

    companion object {
        private const val TEST_USER_ID = "id"
        private const val TEST_TEAM_ID = "teamId"
        private const val TEST_NAME = "name"
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_PHONE = "+49383837373"
        private const val TEST_TRACKING_ID = "id"
        private const val TEST_PICTURE = "picture"
        private const val TEST_ACCENT_ID = 0
        private const val TEST_SKEY = "key"
        private const val TEST_CONNECTION = "unconnected"
        private const val TEST_CONNECTION_TIMESTAMP: Long = 1584372387
        private const val TEST_CONNECTION_MESSAGE = "conn_msg"
        private const val TEST_CONVERSATION = "conversation"
        private const val TEST_RELATION = "Other"
        private const val TEST_TIMESTAMP: Long = 1584372387
        private const val TEST_VERIFIED = "Unknown"
        private const val TEST_DELETED = 0
        private const val TEST_AVAILABILITY = 0
        private const val TEST_HANDLE = "handle"
        private const val TEST_PROVIDER_ID = "id"
        private const val TEST_INTEGRATION_ID = "id"
        private const val TEST_EXPIRES_AT = 0
        private const val TEST_MANAGED_BY = "wire"
        private const val TEST_SELF_PERMISSIONS = 0
        private const val TEST_COPY_PERMISSIONS = 0
        private const val TEST_CREATED_BY = "wire"

        private const val TEST_DB_NAME = "$TEST_USER_ID.db"
    }
}
