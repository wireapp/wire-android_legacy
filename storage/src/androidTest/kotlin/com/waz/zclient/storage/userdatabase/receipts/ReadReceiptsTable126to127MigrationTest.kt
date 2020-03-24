package com.waz.zclient.storage.userdatabase.receipts

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class ReadReceiptsTable126to127MigrationTest : UserDatabaseMigrationTest(126, 127) {

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

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127)

        runBlocking {
            with(allReceipts()[0]) {
                assert(this.messageId == messageId)
                assert(this.userId == userId)
                assert(this.timestamp == timestamp)
            }
        }
    }

    private suspend fun allReceipts() =
        getDatabase().readReceiptsDao().allReceipts()
}
