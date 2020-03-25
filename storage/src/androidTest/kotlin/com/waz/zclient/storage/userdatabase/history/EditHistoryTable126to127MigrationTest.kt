package com.waz.zclient.storage.userdatabase.history

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class EditHistoryTable126to127MigrationTest : UserDatabaseMigrationTest(126, 127) {

    @Test
    fun givenHistoryInsertedIntoEditHistoryTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val originalId = "testOriginalID"
        val updatedId = "testUpdatedID"
        val timestamp = 37227723

        EditHistoryTableTestHelper.insertHistory(
            originalId = originalId,
            updatedId = updatedId,
            timestamp = timestamp,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127)

        runBlocking {
            with(allHistory()[0]) {
                assert(this.originalId == originalId)
                assert(this.updatedId == updatedId)
                assert(this.timestamp == timestamp)
            }
        }
    }

    private suspend fun allHistory() =
        getDatabase().editHistoryDao().allHistory()
}
