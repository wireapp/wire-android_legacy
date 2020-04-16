package com.waz.zclient.storage.userdatabase.errors

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class ErrorsTable126to128MigrationTest : UserDatabaseMigrationTest(126, 128) {

    @Test
    fun givenErrorInsertedIntoErrorsTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val id = "id"
        val type = "type"
        val users = "users"
        val message = "test message"
        val conversationId = "testConvId"
        val resCode = 1
        val resMessage = "message"
        val resLabel = "label"
        val timestamp = 1584698132

        ErrorsTableTestHelper.insertError(
            id = id,
            errorType = type,
            users = users,
            messages = message,
            conversationId = conversationId,
            resCode = resCode,
            resMessage = resMessage,
            resLabel = resLabel,
            time = timestamp,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            val syncJob = allErrors()[0]
            with(syncJob) {
                assert(this.id == id)
                assert(this.errorType == type)
                assert(this.users == users)
                assert(this.messages == message)
                assert(this.conversationId == conversationId)
                assert(this.responseCode == resCode)
                assert(this.responseMessage == resMessage)
                assert(this.responseLabel == resLabel)
                assert(this.time == timestamp)
            }
        }
    }

    private suspend fun allErrors() = getDatabase().errorsDao().allErrors()
}
