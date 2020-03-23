package com.waz.zclient.storage.userdatabase.contact

import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.di.StorageModule.getUserDatabase
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class ContactTables126to127MigrationTest : UserDatabaseMigrationTest(TEST_DB_NAME, 126) {

    @Test
    fun givenContactInsertedIntoContactsTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val id = "testId"
        val name = "testName"
        val nameType = 1
        val sortKey = "testSortKey"
        val searchKey = "testSearchKey"

        ContactsTableTestHelper.insertContact(
            id = id,
            name = name,
            nameType = nameType,
            sortKey = sortKey,
            searchKey = searchKey,
            openHelper = testOpenHelper)

        validateMigration()

        runBlocking {
            with(allContacts()[0]) {
                assert(this.id == id)
                assert(this.name == name)
                assert(this.sortKey == sortKey)
                assert(this.searchKey == searchKey)
            }
        }
    }

    @Test
    fun givenContactOnWireInsertedIntoContactOnWireTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val userId = "testUserId"
        val contactId = "testContactId"

        ContactOnWireTableTestHelper.insertContactOnWire(
            userId = userId,
            contactId = contactId,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            with(allContactOnWire()[0]) {
                assert(this.userId == userId)
                assert(this.contactId == contactId)
            }
        }
    }

    @Test
    fun givenContactHashInsertedIntoContactHashesTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val id = "testId"
        val hashes = "testHashes"

        ContactHashesTableTestHelper.insertContactHashes(
            id = id,
            hashes = hashes,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            with(allContactHashes()[0]) {
                assert(this.id == id)
                assert(this.hashes == hashes)
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

    private suspend fun allContacts() =
        getUserDb().contactsDao().allContacts()

    private suspend fun allContactHashes() =
        getUserDb().contactHashesDao().allContactHashes()

    private suspend fun allContactOnWire() =
        getUserDb().contactOnWireDao().allContactOnWire()

    companion object {
        private const val TEST_DB_NAME = "userDatabase.db"
    }
}
