package com.waz.zclient.storage.userdatabase.sync

import com.waz.zclient.storage.DbSQLiteOpenHelper
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.MigrationTestHelper
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.di.StorageModule.getUserDatabase
import com.waz.zclient.storage.userdatabase.UserDatabaseHelper
import com.waz.zclient.storage.userdatabase.errors.ErrorsTableTestHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ErrorsTable126to127MigrationTest : IntegrationTest() {

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
    fun givenErrorInsertedIntoErrorsTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val id = "id"
        val type = "type"
        val users = "users"
        val message = "test message"
        val conversationId = "testConvId"
        val resCode = 1
        val resMessage = "message"
        val resLabel = "label"
        val timestamp = 1584698132

        ErrorsTableTestHelper.insertError(
            id = id,
            errorType = type,
            users = users,
            messages = message,
            conversationId = conversationId,
            resCode = resCode,
            resMessage = resMessage,
            resLabel = resLabel,
            time = timestamp,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            val syncJob = allErrors()[0]
            with(syncJob) {
                assert(this.id == id)
                assert(this.errorType == type)
                assert(this.users == users)
                assert(this.messages == message)
                assert(this.conversationId == conversationId)
                assert(this.responseCode == resCode)
                assert(this.responseMessage == resMessage)
                assert(this.responseLabel == resLabel)
                assert(this.time == timestamp)
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

    private suspend fun allErrors() =
        getUserDb().errorsDao().allErrors()

    companion object {
        private const val TEST_DB_NAME = "userDatabase.db"
    }
}
