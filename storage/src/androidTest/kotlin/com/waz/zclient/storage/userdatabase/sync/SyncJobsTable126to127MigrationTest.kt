package com.waz.zclient.storage.userdatabase.sync

import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.di.StorageModule.getUserDatabase
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class SyncJobsTable126to127MigrationTest : UserDatabaseMigrationTest(TEST_DB_NAME, 126) {

    @Test
    fun givenSyncJobInsertedIntoSyncJobsTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val id = "testId"
        val data = "testData"

        SyncJobsTableTestHelper.insertSyncJob(
            id = id,
            data = data,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            with(allSyncJobs()[0]) {
                assert(this.id == id)
                assert(this.data == data)
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

    private suspend fun allSyncJobs() =
        getUserDb().syncJobsDao().allSyncJobs()

    companion object {
        private const val TEST_DB_NAME = "userDatabase.db"
    }
}
