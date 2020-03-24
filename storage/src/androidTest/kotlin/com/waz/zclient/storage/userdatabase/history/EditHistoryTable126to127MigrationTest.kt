package com.waz.zclient.storage.userdatabase.history

import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.di.StorageModule.getUserDatabase
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class EditHistoryTable126to127MigrationTest : UserDatabaseMigrationTest(TEST_DB_NAME, 126) {

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

        validateMigration()

        runBlocking {
            with(allHistory()[0]) {
                assert(this.originalId == originalId)
                assert(this.updatedId == updatedId)
                assert(this.timestamp == timestamp)
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

    private suspend fun allHistory() =
        getUserDb().editHistoryDao().allHistory()

    companion object {
        private const val TEST_DB_NAME = "userDatabase.db"
    }
}
