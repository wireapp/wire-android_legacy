package com.waz.zclient.storage.userdatabase.messages

import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.di.StorageModule.getUserDatabase
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Test

@ExperimentalCoroutinesApi
class MessagesTables126to127MigrationTest : UserDatabaseMigrationTest(TEST_DB_NAME, 126) {

    @Test
    fun givenMessageInsertedIntoMessagesTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val id = "testId"
        val conversationId = "testId"
        val messageType = "testType"
        val userId = "testUserId"
        val content = "testContent"
        val proto = ByteArray(5)
        val time = 1584710479
        val message = 303037372
        val members = "testMembers"
        val recipient = "testRecipient"
        val email = "test@wire.com"
        val name = "testName"
        val messageState = "delivered"
        val contentSize = 5000
        val ephemeral = 40
        val expired = 1
        val duration = 5050505
        val quote = "testQuote"
        val quoteValidity = 9000
        val forceReadReceipts = 0
        val assetId = "testAssetId"

        MessagesTableTestHelper.insertMessage(
            id = id,
            conversationId = conversationId,
            messageType = messageType,
            userId = userId,
            content = content,
            protos = proto,
            time = time,
            localTime = time,
            firstMessage = message,
            members = members,
            recipient = recipient,
            email = email,
            name = name,
            messageState = messageState,
            contentSize = contentSize,
            editTime = time,
            ephemeral = ephemeral,
            expiryTime = time,
            expired = expired,
            duration = duration,
            quote = quote,
            quoteValidity = quoteValidity,
            forceReadReceipts = forceReadReceipts,
            assetId = assetId,
            openHelper = testOpenHelper)

        validateMigration()

        runBlocking {
            with(allMessages()[0]) {
                assert(this.id == id)
                assert(this.conversationId == conversationId)
                assert(this.messageType == messageType)
                assert(this.userId == userId)
                assert(this.content == content)
                assert(this.protos!!.contentEquals(protos!!))
                assert(this.time == time)
                assert(firstMessage == message)
                assert(this.members == members)
                assert(this.recipient == recipient)
                assert(this.email == email)
                assert(this.name == name)
                assert(this.messageState == messageState)
                assert(this.contentSize == contentSize)
                assert(this.editTime == editTime)
                assert(this.ephemeral == ephemeral)
                assert(this.expiryTime == expiryTime)
                assert(this.expired == expired)
                assert(this.duration == duration)
                assert(this.quote == quote)
                assert(this.quoteValidity == quoteValidity)
                assert(this.forceReadReceipts == forceReadReceipts)
                assert(this.assetId == assetId)
            }
        }
    }

    @Test
    fun givenMessageDeletionInsertedIntoMessageDeletionTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val messageId = "testMessageId"
        val timestamp = 1584710479

        MessageDeletionTableTestHelper.insertMessageDeletion(
            messageId = messageId,
            timestamp = timestamp,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            with(allMessageDeletions()[0]) {
                assert(this.messageId == messageId)
                assert(this.timestamp == timestamp)
            }
        }
    }

    @Test
    fun givenLikeInsertedIntoLikesTableVersion126_whenMigratedToVersion127_thenAssertDataIsStillIntact() {

        val messageId = "testMessageId"
        val userId = "testUserId"
        val timestamp = 1584710479
        val action = 10


        LikesTableTestHelper.insertLike(
            messageId = messageId,
            userId = userId,
            timestamp = timestamp,
            action = action,
            openHelper = testOpenHelper
        )

        validateMigration()

        runBlocking {
            with(allLikes()[0]) {
                assert(this.messageId == messageId)
                assert(this.userId == userId)
                assert(this.timeStamp == timestamp)
                assert(this.action == action)
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

    private suspend fun allMessages() =
        getUserDb().messagesDao().allMessages()


    private suspend fun allMessageDeletions() =
        getUserDb().messagesDeletionDao().allMessageDeletions()

    private suspend fun allLikes() =
        getUserDb().likesDao().allLikes()

    companion object {
        private const val TEST_DB_NAME = "userDatabase.db"
    }
}
