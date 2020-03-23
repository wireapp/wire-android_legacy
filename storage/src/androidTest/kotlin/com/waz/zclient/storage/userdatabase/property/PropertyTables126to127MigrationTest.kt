package com.waz.zclient.storage.userdatabase.property

import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.di.StorageModule.getUserDatabase
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class PropertyTables126to127MigrationTest : UserDatabaseMigrationTest(TEST_DB_NAME, 126) {

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

    private suspend fun allKeyValues() =
        getUserDb().keyValuesDao().allKeyValues()


    private suspend fun allProperties() =
        getUserDb().propertiesDao().allProperties()

    companion object {
        private const val TEST_DB_NAME = "userDatabase.db"
    }
}
