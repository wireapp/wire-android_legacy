package com.waz.zclient.storage.userdatabase.property

import com.waz.zclient.storage.DbSQLiteOpenHelper
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.MigrationTestHelper
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.di.StorageModule.getUserDatabase
import com.waz.zclient.storage.userdatabase.UserDatabaseHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class PropertyTables126to127MigrationTest : IntegrationTest() {

    private lateinit var testOpenHelper: DbSQLiteOpenHelper

    private val databaseHelper: UserDatabaseHelper by lazy {
        UserDatabaseHelper()
    }

    private lateinit var testHelper: MigrationTestHelper

    @Before
    fun setUp() {
        testHelper = MigrationTestHelper(UserDatabase::class.java.canonicalName)
        testOpenHelper = DbSQLiteOpenHelper(getApplicationContext(),
            TEST_DB_NAME, 126)
        databaseHelper.createDatabase(testOpenHelper)
    }

    @After
    fun tearDown() {
        databaseHelper.clearDatabase(testOpenHelper)
    }

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
