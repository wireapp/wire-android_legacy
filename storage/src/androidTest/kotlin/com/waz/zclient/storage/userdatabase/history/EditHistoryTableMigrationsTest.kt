package com.waz.zclient.storage.userdatabase.history

import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class EditHistoryTableMigrationsTest : UserDatabaseMigrationTest() {

    @Test
    fun givenHistoryInsertedIntoEditHistoryTable_whenMigrationDone_thenAssertDataIsStillIntact() {

        val originalId = "testOriginalID"
        val updatedId = "testUpdatedID"
        val timestamp = 37227723

        EditHistoryTableTestHelper.insertHistory(
            originalId = originalId,
            updatedId = updatedId,
            timestamp = timestamp,
            openHelper = testOpenHelper
        )

        validateMigrations()

        runBlocking {
            with(allHistory()[0]) {
                assert(this.originalId == originalId)
                assert(this.updatedId == updatedId)
                assert(this.timestamp == timestamp)
            }
        }
    }

    private suspend fun allHistory() =
        getUserDatabase().editHistoryDao().allHistory()
}
