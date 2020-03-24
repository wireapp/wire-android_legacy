package com.waz.zclient.storage.userdatabase.email

import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class EmailAddressesTableMigrationsTest : UserDatabaseMigrationTest() {

    @Test
    fun givenEmailAddressInsertedIntoEmailAddressesTable_whenMigrationDone_thenAssertDataIsStillIntact() {

        val contactId = "testContactId"
        val email = "test@wire.com"

        EmailAddressesTableTestHelper.insertEmailAddress(
            contactId = contactId,
            email = email,
            openHelper = testOpenHelper
        )

        validateMigrations()

        runBlocking {
            with(allEmailAddresses()[0]) {
                assert(this.contactId == contactId)
                assert(this.emailAddress == email)
            }
        }
    }

    private suspend fun allEmailAddresses() =
        getUserDatabase().emailAddressesDao().allEmailAddresses()
}
