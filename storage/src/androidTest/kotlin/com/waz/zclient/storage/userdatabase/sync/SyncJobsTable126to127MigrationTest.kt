package com.waz.zclient.storage.userdatabase.sync

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class SyncJobsTable126to127MigrationTest : UserDatabaseMigrationTest(126, 127) {

    @Test
    fun givenSyncJobInsertedIntoSyncJobsTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val id = "testId"
        val data = "testData"

        SyncJobsTableTestHelper.insertSyncJob(
            id = id,
            data = data,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127)

        runBlocking {
            with(allSyncJobs()[0]) {
                assert(this.id == id)
                assert(this.data == data)
            }
        }
    }

    private suspend fun allSyncJobs() =
        getDatabase().syncJobsDao().allSyncJobs()
}
