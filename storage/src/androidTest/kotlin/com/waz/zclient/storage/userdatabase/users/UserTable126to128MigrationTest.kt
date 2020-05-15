package com.waz.zclient.storage.userdatabase.users

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class UserTable126to128MigrationTest : UserDatabaseMigrationTest(126, 128) {

    @Test
    fun givenUserInsertedIntoUsersTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val userId = "testUserId"
        val teamId = "testTeamId"
        val name = "testName"
        val email = "test@wire.com"
        val phone = "+4947474747474646464"
        val trackingId = "testTrackingId"
        val picture = "testPicture"
        val accentId = 0
        val sKey = "testSkey"
        val connection = "unconnected"
        val connectionTimestamp = 1584372387
        val connectionMessage = "testConnectionMessage"
        val conversation = "testConversation"
        val relation = "Other"
        val timestamp = 1584372387
        val verified = "Unknown"
        val deleted = false
        val availability = 0
        val handle = "testHandle"
        val providerId = "providerId"
        val integrationId = "testIntegrationId"
        val expiresAt = 0
        val managedBy = "wire"
        val selfPermission = 0
        val copyPermission = 0
        val createdBy = "wire"

        UsersTableTestHelper.insertUser(
            id = userId,
            teamId = teamId,
            name = name,
            email = email,
            phone = phone,
            trackingId = trackingId,
            picture = picture,
            accentId = accentId,
            sKey = sKey,
            connection = connection,
            connectionTimestamp = connectionTimestamp,
            connectionMessage = connectionMessage,
            conversation = conversation,
            relation = relation,
            timestamp = timestamp,
            verified = verified,
            deleted = deleted,
            availability = availability,
            handle = handle,
            providerId = providerId,
            integrationId = integrationId,
            expiresAt = expiresAt,
            managedBy = managedBy,
            selfPermission = selfPermission,
            copyPermission = copyPermission,
            createdBy = createdBy,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(getAllUsers()[0]) {
                assertEquals(this.id, id)
                assertEquals(this.teamId, teamId)
                assertEquals(this.name, name)
                assertEquals(this.email, email)
                assertEquals(this.phone, phone)
                assertEquals(this.trackingId, trackingId)
                assertEquals(this.picture, picture)
                assertEquals(this.accentId, accentId)
                assertEquals(this.sKey, sKey)
                assertEquals(this.connection, connection)
                assertEquals(this.connectionTimestamp, connectionTimestamp)
                assertEquals(this.connectionMessage, connectionMessage)
                assertEquals(this.conversation, conversation)
                assertEquals(this.relation, relation)
                assertEquals(this.timestamp, timestamp)
                assertEquals(this.verified, verified)
                assertEquals(this.deleted, deleted)
                assertEquals(this.availability, availability)
                assertEquals(this.handle, handle)
                assertEquals(this.providerId, providerId)
                assertEquals(this.integrationId, integrationId)
                assertEquals(this.expiresAt, expiresAt)
                assertEquals(this.managedBy, managedBy)
                assertEquals(this.selfPermission, selfPermission)
                assertEquals(this.copyPermission, copyPermission)
                assertEquals(this.createdBy, createdBy)
            }
        }
    }

    private suspend fun getAllUsers() = getDatabase().userDao().allUsers()
}
