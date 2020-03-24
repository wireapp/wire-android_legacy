package com.waz.zclient.storage.userdatabase.property

import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class PropertyTablesMigrationsTest : UserDatabaseMigrationTest() {

    @Test
    fun givenKeyValueInsertedIntoMessagesTable_whenMigrationDone_thenAssertDataIsStillIntact() {

        val key = "testKey"
        val value = "testValue"

        KeyValueTableTestHelper.insertKeyValue(
            key = key,
            value = value,
            openHelper = testOpenHelper)

        validateMigrations()

        runBlocking {
            with(allKeyValues()[0]) {
                assert(this.key == key)
                assert(this.value == value)
            }
        }
    }

    @Test
    fun givenPropertyInsertedIntoPropertiesTable_whenMigrationDone_thenAssertDataIsStillIntact() {

        val key = "testKey"
        val value = "testValue"

        PropertiesTableTestHelper.insertProperty(
            key = key,
            value = value,
            openHelper = testOpenHelper
        )

        validateMigrations()

        runBlocking {
            with(allProperties()[0]) {
                assert(this.key == key)
                assert(this.value == value)
            }
        }
    }

    private suspend fun allKeyValues() =
        getUserDatabase().keyValuesDao().allKeyValues()

    private suspend fun allProperties() =
        getUserDatabase().propertiesDao().allProperties()
}
