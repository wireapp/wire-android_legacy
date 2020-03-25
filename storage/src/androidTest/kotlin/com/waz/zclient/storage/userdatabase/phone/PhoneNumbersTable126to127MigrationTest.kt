package com.waz.zclient.storage.userdatabase.phone

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class PhoneNumbersTable126to127MigrationTest : UserDatabaseMigrationTest(126, 127) {

    @Test
    fun givenEmailAddressInsertedIntoEmailAddressesTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val contactId = "testContactId"
        val phone = "+49347474746464644"

        PhoneNumbersTableTestHelper.insertPhoneNumber(
            contactId = contactId,
            phone = phone,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127)

        runBlocking {
            with(allPhoneNumbers()[0]) {
                assert(this.contactId == contactId)
                assert(this.phoneNumber == phone)
            }
        }
    }

    private suspend fun allPhoneNumbers() =
        getDatabase().phoneNumbersDao().allPhoneNumbers()
}
