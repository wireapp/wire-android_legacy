package com.waz.zclient.storage.userdatabase.contact

import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class ContactTablesMigrationsTest : UserDatabaseMigrationTest() {

    @Test
    fun givenContactInsertedIntoContactsTable_whenMigrationDone_thenAssertDataIsStillIntact() {

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

        validateMigrations()

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
    fun givenContactOnWireInsertedIntoContactOnWireTable_whenMigrationDone_thenAssertDataIsStillIntact() {

        val userId = "testUserId"
        val contactId = "testContactId"

        ContactOnWireTableTestHelper.insertContactOnWire(
            userId = userId,
            contactId = contactId,
            openHelper = testOpenHelper
        )

        validateMigrations()

        runBlocking {
            with(allContactOnWire()[0]) {
                assert(this.userId == userId)
                assert(this.contactId == contactId)
            }
        }
    }

    @Test
    fun givenContactHashInsertedIntoContactHashesTable_whenMigrationDone_thenAssertDataIsStillIntact() {

        val id = "testId"
        val hashes = "testHashes"

        ContactHashesTableTestHelper.insertContactHashes(
            id = id,
            hashes = hashes,
            openHelper = testOpenHelper
        )

        validateMigrations()

        runBlocking {
            with(allContactHashes()[0]) {
                assert(this.id == id)
                assert(this.hashes == hashes)
            }
        }
    }

    private suspend fun allContacts() =
        getUserDatabase().contactsDao().allContacts()

    private suspend fun allContactHashes() =
        getUserDatabase().contactHashesDao().allContactHashes()

    private suspend fun allContactOnWire() =
        getUserDatabase().contactOnWireDao().allContactOnWire()
}
