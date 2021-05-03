package com.waz.zclient.storage.userdatabase.property

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class PropertyTables127to128MigrationTest : UserDatabaseMigrationTest(127, 128) {

    @Test
    fun givenKeyValueInsertedIntoMessagesTableVersion127_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val key = "testKey"
        val value = "testValue"

        KeyValueTableTestHelper.insertKeyValue(
            key = key,
            value = value,
            openHelper = testOpenHelper)

        validateMigration(USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allKeyValues()[0]) {
                assertEquals(this.key, key)
                assertEquals(this.value, value)
            }
        }
    }

    @Test
    fun givenPropertyInsertedIntoPropertiesTableVersion127_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val key = "testKey"
        val value = "testValue"

        PropertiesTableTestHelper.insertProperty(
            key = key,
            value = value,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allProperties()[0]) {
                assertEquals(this.key, key)
                assertEquals(this.value, value)
            }
        }
    }

    private suspend fun allKeyValues() =
        getDatabase().keyValuesDao().allKeyValues()

    private suspend fun allProperties() =
        getDatabase().propertiesDao().allProperties()
}
