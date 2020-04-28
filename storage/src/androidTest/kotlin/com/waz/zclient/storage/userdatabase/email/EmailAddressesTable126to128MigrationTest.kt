package com.waz.zclient.storage.userdatabase.email

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class EmailAddressesTable126to128MigrationTest : UserDatabaseMigrationTest(126, 128) {

    @Test
    fun givenEmailAddressInsertedIntoEmailAddressesTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val contactId = "testContactId"
        val email = "test@wire.com"

        EmailAddressesTableTestHelper.insertEmailAddress(
            contactId = contactId,
            email = email,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)


        runBlocking {
            with(allEmailAddresses()[0]) {
                assertEquals(this.contactId, contactId)
                assertEquals(this.emailAddress, email)
            }
        }
    }

    private suspend fun allEmailAddresses() =
        getDatabase().emailAddressesDao().allEmailAddresses()
}
