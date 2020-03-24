package com.waz.zclient.storage.userdatabase

import com.waz.zclient.storage.DbSQLiteOpenHelper
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.MigrationTestHelper
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.di.StorageModule
import org.junit.After
import org.junit.Before

abstract class UserDatabaseMigrationTest : IntegrationTest() {

    protected lateinit var testOpenHelper: DbSQLiteOpenHelper

    private val databaseHelper: UserDatabaseHelper by lazy {
        UserDatabaseHelper()
    }

    private lateinit var testHelper: MigrationTestHelper

    @Before
    fun setUp() {
        testHelper = MigrationTestHelper(UserDatabase::class.java.canonicalName)
        testOpenHelper = DbSQLiteOpenHelper(getApplicationContext(),
            TEST_DB_NAME, START_VERSION)
        databaseHelper.createDatabase(testOpenHelper)
    }

    @After
    fun tearDown() {
        databaseHelper.clearDatabase(testOpenHelper)
    }

    protected fun getUserDatabase() = StorageModule.getUserDatabase(
        getApplicationContext(), TEST_DB_NAME, UserDatabase.migrations
    )

    protected fun validateMigrations() =
        testHelper.validateMigration(
            dbName = TEST_DB_NAME,
            dbVersion = END_VERSION,
            validateDroppedTables = true,
            migrations = *UserDatabase.migrations
        )

    companion object {
        private const val TEST_DB_NAME = "UserDatabase.db"
        private const val START_VERSION = 126

        // Increment END_VERSION to test future migrations
        private const val END_VERSION = 127
    }
}
