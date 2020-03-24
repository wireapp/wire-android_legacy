package com.waz.zclient.storage.userdatabase.receipts

import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.di.StorageModule.getUserDatabase
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class ReadReceiptsTable126to127MigrationTest : UserDatabaseMigrationTest(TEST_DB_NAME, 126) {

    @Test
    fun givenReceiptsInsertedIntoReadReceiptsTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val messageId = "messageId"
        val userId = "UserId"
        val timestamp = 48484

        ReadReceiptsTableTestHelper.insertReceipt(
            messageId = messageId,
            userId = userId,
            timestamp = timestamp,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            with(allReceipts()[0]) {
                assert(this.messageId == messageId)
                assert(this.userId == userId)
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

    private suspend fun allReceipts() =
        getUserDb().readReceiptsDao().allReceipts()

    companion object {
        private const val TEST_DB_NAME = "userDatabase.db"
    }
}
