package com.waz.zclient.storage.userdatabase.email

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class EmailAddressesTable126to127MigrationTest : UserDatabaseMigrationTest(126, 127) {

    @Test
    fun givenEmailAddressInsertedIntoEmailAddressesTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val contactId = "testContactId"
        val email = "test@wire.com"

        EmailAddressesTableTestHelper.insertEmailAddress(
            contactId = contactId,
            email = email,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127)

        runBlocking {
            with(allEmailAddresses()[0]) {
                assert(this.contactId == contactId)
                assert(this.emailAddress == email)
            }
        }
    }

    private suspend fun allEmailAddresses() =
        getDatabase().emailAddressesDao().allEmailAddresses()
}
