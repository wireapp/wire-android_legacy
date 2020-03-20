package com.waz.zclient.storage.userdatabase.errors

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
        ErrorsTableTestHelper.insertError(
            id = TEST_ERRORS_ID,
            errorType = TEST_ERRORS_TYPE,
            users = TEST_ERRORS_USERS,
            messages = TEST_ERRORS_MESSAGES,
            conversationId = TEST_ERRORS_CONV_ID,
            resCode = TEST_ERRORS_RES_CODE,
            resMessage = TEST_ERRORS_RES_MESSAGE,
            resLabel = TEST_ERRORS_RES_LABEL,
            time = TEST_ERRORS_TIME,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            val syncJob = allErrors()[0]
            with(syncJob) {
                assert(id == TEST_ERRORS_ID)
                assert(errorType == TEST_ERRORS_TYPE)
                assert(errorType == TEST_ERRORS_TYPE)
                assert(users == TEST_ERRORS_USERS)
                assert(messages == TEST_ERRORS_MESSAGES)
                assert(conversationId == TEST_ERRORS_CONV_ID)
                assert(responseCode == TEST_ERRORS_RES_CODE)
                assert(responseMessage == TEST_ERRORS_RES_MESSAGE)
                assert(responseLabel == TEST_ERRORS_RES_LABEL)
                assert(time == TEST_ERRORS_TIME)
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
        private const val TEST_ERRORS_ID = "id"
        private const val TEST_ERRORS_TYPE = "type"
        private const val TEST_ERRORS_USERS = "users"
        private const val TEST_ERRORS_MESSAGES = "test message"
        private const val TEST_ERRORS_CONV_ID = "conv_id"
        private const val TEST_ERRORS_RES_CODE = 1
        private const val TEST_ERRORS_RES_MESSAGE = "message"
        private const val TEST_ERRORS_RES_LABEL = "label"
        private const val TEST_ERRORS_TIME = 1584698132
    }
}
