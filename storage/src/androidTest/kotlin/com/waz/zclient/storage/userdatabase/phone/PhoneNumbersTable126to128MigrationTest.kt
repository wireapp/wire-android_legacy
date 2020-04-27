package com.waz.zclient.storage.userdatabase.phone

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class PhoneNumbersTable126to128MigrationTest : UserDatabaseMigrationTest(126, 128) {

    @Test
    fun givenEmailAddressInsertedIntoEmailAddressesTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val contactId = "testContactId"
        val phone = "+49347474746464644"

        PhoneNumbersTableTestHelper.insertPhoneNumber(
            contactId = contactId,
            phone = phone,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allPhoneNumbers()[0]) {
                assertEquals(this.contactId, contactId)
                assertEquals(this.phoneNumber, phone)
            }
        }
    }

    private suspend fun allPhoneNumbers() =
        getDatabase().phoneNumbersDao().allPhoneNumbers()
}
