package com.waz.zclient.storage.userdatabase.phone

import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.di.StorageModule.getUserDatabase
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class PhoneNumbersTable126to127MigrationTest : UserDatabaseMigrationTest(TEST_DB_NAME, 126) {

    @Test
    fun givenEmailAddressInsertedIntoEmailAddressesTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val contactId = "testContactId"
        val phone = "+49347474746464644"

        PhoneNumbersTableTestHelper.insertPhoneNumber(
            contactId = contactId,
            phone = phone,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            val syncJob = allPhoneNumbers()[0]
            with(syncJob) {
                assert(this.contactId == contactId)
                assert(this.phoneNumber == phone)
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

    private suspend fun allPhoneNumbers() =
        getUserDb().phoneNumbersDao().allPhoneNumbers()

    companion object {
        private const val TEST_DB_NAME = "userDatabase.db"
    }
}
