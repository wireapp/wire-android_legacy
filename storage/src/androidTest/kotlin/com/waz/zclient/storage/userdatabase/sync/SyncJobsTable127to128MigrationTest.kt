package com.waz.zclient.storage.userdatabase.sync

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class SyncJobsTable127to128MigrationTest : UserDatabaseMigrationTest(127, 128) {

    @Test
    fun givenSyncJobInsertedIntoSyncJobsTableVersion127_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val id = "testId"
        val data = "testData"

        SyncJobsTableTestHelper.insertSyncJob(
            id = id,
            data = data,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allSyncJobs()[0]) {
                assertEquals(this.id, id)
                assertEquals(this.data, data)
            }
        }
    }

    private suspend fun allSyncJobs() =
        getDatabase().syncJobsDao().allSyncJobs()
}
