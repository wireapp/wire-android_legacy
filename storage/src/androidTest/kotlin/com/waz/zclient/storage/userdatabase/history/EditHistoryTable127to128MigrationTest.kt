package com.waz.zclient.storage.userdatabase.history

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class EditHistoryTable127to128MigrationTest : UserDatabaseMigrationTest(127, 128) {

    @Test
    fun givenHistoryInsertedIntoEditHistoryTableVersion127_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val originalId = "testOriginalID"
        val updatedId = "testUpdatedID"
        val timestamp = 37227723

        EditHistoryTableTestHelper.insertHistory(
            originalId = originalId,
            updatedId = updatedId,
            timestamp = timestamp,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allHistory()[0]) {
                assertEquals(this.originalId, originalId)
                assertEquals(this.updatedId, updatedId)
                assertEquals(this.timestamp, timestamp)
            }
        }
    }

    private suspend fun allHistory() =
        getDatabase().editHistoryDao().allHistory()
}
