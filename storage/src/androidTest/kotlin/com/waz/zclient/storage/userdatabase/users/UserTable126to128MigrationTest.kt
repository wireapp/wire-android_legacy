package com.waz.zclient.storage.userdatabase.users

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
                assert(this.id == id)
                assert(this.teamId == teamId)
                assert(this.name == name)
                assert(this.email == email)
                assert(this.phone == phone)
                assert(this.trackingId == trackingId)
                assert(this.picture == picture)
                assert(this.accentId == accentId)
                assert(this.sKey == sKey)
                assert(this.connection == connection)
                assert(this.connectionTimestamp == connectionTimestamp)
                assert(this.connectionMessage == connectionMessage)
                assert(this.conversation == conversation)
                assert(this.relation == relation)
                assert(this.timestamp == timestamp)
                assert(this.verified == verified)
                assert(this.deleted == deleted)
                assert(this.availability == availability)
                assert(this.handle == handle)
                assert(this.providerId == providerId)
                assert(this.integrationId == integrationId)
                assert(this.expiresAt == expiresAt)
                assert(this.managedBy == managedBy)
                assert(this.selfPermission == selfPermission)
                assert(this.copyPermission == copyPermission)
                assert(this.createdBy == createdBy)
            }
        }
    }

    private suspend fun getAllUsers() = getDatabase().userDao().allUsers()
}
