package com.waz.zclient.storage.userdatabase.property

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class PropertyTables126to127MigrationTest : UserDatabaseMigrationTest(126,
    127, USER_DATABASE_MIGRATION_126_TO_127) {

    @Test
    fun givenKeyValueInsertedIntoMessagesTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val key = "testKey"
        val value = "testValue"

        KeyValueTableTestHelper.insertKeyValue(
            key = key,
            value = value,
            openHelper = testOpenHelper)

        validateMigration()

        runBlocking {
            with(allKeyValues()[0]) {
                assert(this.key == key)
                assert(this.value == value)
            }
        }
    }

    @Test
    fun givenPropertyInsertedIntoPropertiesTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val key = "testKey"
        val value = "testValue"

        PropertiesTableTestHelper.insertProperty(
            key = key,
            value = value,
            openHelper = testOpenHelper
        )

        validateMigration()

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
