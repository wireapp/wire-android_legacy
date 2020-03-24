package com.waz.zclient.storage.userdatabase.receipts

import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class ReadReceiptsTableMigrationsTest : UserDatabaseMigrationTest() {

    @Test
    fun givenReceiptsInsertedIntoReadReceiptsTable_whenMigrationDone_thenAssertDataIsStillIntact() {

        val messageId = "messageId"
        val userId = "UserId"
        val timestamp = 48484

        ReadReceiptsTableTestHelper.insertReceipt(
            messageId = messageId,
            userId = userId,
            timestamp = timestamp,
            openHelper = testOpenHelper
        )

        validateMigrations()

        runBlocking {
            with(allReceipts()[0]) {
                assert(this.messageId == messageId)
                assert(this.userId == userId)
                assert(this.timestamp == timestamp)
            }
        }
    }

    private suspend fun allReceipts() =
        getUserDatabase().readReceiptsDao().allReceipts()
}
