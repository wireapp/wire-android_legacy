package com.waz.zclient.storage.userdatabase

import androidx.room.migration.Migration
import com.waz.zclient.storage.DbSQLiteOpenHelper
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.MigrationTestHelper
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.di.StorageModule
import org.junit.After
import org.junit.Before

abstract class UserDatabaseMigrationTest(
    private val startVersion: Int,
    private val endVersion: Int
) : IntegrationTest() {

    protected lateinit var testOpenHelper: DbSQLiteOpenHelper

    private val databaseHelper: UserDatabaseHelper by lazy {
        UserDatabaseHelper()
    }

    private lateinit var testHelper: MigrationTestHelper

    @Before
    fun setUp() {
        testHelper = MigrationTestHelper(UserDatabase::class.java.canonicalName)
        testOpenHelper = DbSQLiteOpenHelper(getApplicationContext(),
            TEST_DB_NAME, startVersion)
        databaseHelper.createDatabase(testOpenHelper)
    }

    @After
    fun tearDown() {
        databaseHelper.clearDatabase(testOpenHelper)
    }

    protected fun getDatabase() = StorageModule.getUserDatabase(
        getApplicationContext(), TEST_DB_NAME, UserDatabase.migrations
    )

    protected fun validateMigration(vararg migrations: Migration) =
        testHelper.validateMigration(
            dbName = TEST_DB_NAME,
            dbVersion = endVersion,
            validateDroppedTables = true,
            migrations = *migrations
        )

    companion object {
        private const val TEST_DB_NAME = "UserDatabase.db"
    }
}
