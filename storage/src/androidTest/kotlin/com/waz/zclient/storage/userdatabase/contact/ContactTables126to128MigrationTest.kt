package com.waz.zclient.storage.userdatabase.contact

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class ContactTables126to128MigrationTest : UserDatabaseMigrationTest(126, 128) {

    @Test
    fun givenContactInsertedIntoContactsTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

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

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allContacts()[0]) {
                assertEquals(this.id, id)
                assertEquals(this.name, name)
                assertEquals(this.sortKey, sortKey)
                assertEquals(this.searchKey, searchKey)
            }
        }
    }

    @Test
    fun givenContactOnWireInsertedIntoContactOnWireTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val userId = "testUserId"
        val contactId = "testContactId"

        ContactOnWireTableTestHelper.insertContactOnWire(
            userId = userId,
            contactId = contactId,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allContactOnWire()[0]) {
                assertEquals(this.userId, userId)
                assertEquals(this.contactId, contactId)
            }
        }
    }

    @Test
    fun givenContactHashInsertedIntoContactHashesTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val id = "testId"
        val hashes = "testHashes"

        ContactHashesTableTestHelper.insertContactHashes(
            id = id,
            hashes = hashes,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allContactHashes()[0]) {
                assertEquals(this.id, id)
                assertEquals(this.hashes, hashes)
            }
        }
    }

    private suspend fun allContacts() =
        getDatabase().contactsDao().allContacts()

    private suspend fun allContactHashes() =
        getDatabase().contactHashesDao().allContactHashes()

    private suspend fun allContactOnWire() =
        getDatabase().contactOnWireDao().allContactOnWire()
}
