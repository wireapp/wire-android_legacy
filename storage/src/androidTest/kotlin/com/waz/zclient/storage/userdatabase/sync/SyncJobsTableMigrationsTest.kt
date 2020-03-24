package com.waz.zclient.storage.userdatabase.sync

import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class SyncJobsTableMigrationsTest : UserDatabaseMigrationTest() {

    @Test
    fun givenSyncJobInsertedIntoSyncJobsTable_whenMigrationDone_thenAssertDataIsStillIntact() {

        val id = "testId"
        val data = "testData"

        SyncJobsTableTestHelper.insertSyncJob(
            id = id,
            data = data,
            openHelper = testOpenHelper
        )

        validateMigrations()

        runBlocking {
            with(allSyncJobs()[0]) {
                assert(this.id == id)
                assert(this.data == data)
            }
        }
    }

    private suspend fun allSyncJobs() =
        getUserDatabase().syncJobsDao().allSyncJobs()
}
