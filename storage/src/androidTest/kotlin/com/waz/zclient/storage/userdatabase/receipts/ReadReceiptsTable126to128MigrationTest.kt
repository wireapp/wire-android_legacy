package com.waz.zclient.storage.userdatabase.receipts

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

@ExperimentalCoroutinesApi
class ReadReceiptsTable126to128MigrationTest : UserDatabaseMigrationTest(126, 128) {

    @Test
    fun givenReceiptsInsertedIntoReadReceiptsTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val messageId = "messageId"
        val userId = "UserId"
        val timestamp = 48484

        ReadReceiptsTableTestHelper.insertReceipt(
            messageId = messageId,
            userId = userId,
            timestamp = timestamp,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allReceipts()[0]) {
                assertEquals(this.messageId, messageId)
                assertEquals(this.userId, userId)
                assertEquals(this.timestamp, timestamp)
            }
        }
    }

    private suspend fun allReceipts() =
        getDatabase().readReceiptsDao().allReceipts()
}
