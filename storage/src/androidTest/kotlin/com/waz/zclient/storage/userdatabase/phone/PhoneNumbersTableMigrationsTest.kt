package com.waz.zclient.storage.userdatabase.phone

import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class PhoneNumbersTableMigrationsTest : UserDatabaseMigrationTest() {

    @Test
    fun givenEmailAddressInsertedIntoEmailAddressesTable_whenMigrationDone_thenAssertDataIsStillIntact() {

        val contactId = "testContactId"
        val phone = "+49347474746464644"

        PhoneNumbersTableTestHelper.insertPhoneNumber(
            contactId = contactId,
            phone = phone,
            openHelper = testOpenHelper
        )

        validateMigrations()

        runBlocking {
            with(allPhoneNumbers()[0]) {
                assert(this.contactId == contactId)
                assert(this.phoneNumber == phone)
            }
        }
    }

    private suspend fun allPhoneNumbers() =
        getUserDatabase().phoneNumbersDao().allPhoneNumbers()
}
