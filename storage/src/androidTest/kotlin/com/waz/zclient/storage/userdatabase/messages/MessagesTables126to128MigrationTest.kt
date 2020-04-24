package com.waz.zclient.storage.userdatabase.messages

import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_126_TO_127
import com.waz.zclient.storage.db.users.migration.USER_DATABASE_MIGRATION_127_TO_128
import com.waz.zclient.storage.userdatabase.UserDatabaseMigrationTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@ExperimentalCoroutinesApi
class MessagesTables126to128MigrationTest : UserDatabaseMigrationTest(126, 128) {

    @Test
    fun givenMessageInsertedIntoMessagesTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val id = "testId"
        val conversationId = "testId"
        val messageType = "testType"
        val userId = "testUserId"
        val content = "testContent"
        val proto = ByteArray(5)
        val time = 1584710479
        val message = false
        val members = "testMembers"
        val recipient = "testRecipient"
        val email = "test@wire.com"
        val name = "testName"
        val messageState = "delivered"
        val contentSize = 5000
        val ephemeral = 40
        val expired = true
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

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allMessages()[0]) {
                assertEquals(this.id, id)
                assertEquals(this.conversationId, conversationId)
                assertEquals(this.messageType, messageType)
                assertEquals(this.userId, userId)
                assertEquals(this.content, content)
                assertTrue(this.protos!!.contentEquals(protos!!))
                assertEquals(this.time, time)
                assertEquals(firstMessage, message)
                assertEquals(this.members, members)
                assertEquals(this.recipient, recipient)
                assertEquals(this.email, email)
                assertEquals(this.name, name)
                assertEquals(this.messageState, messageState)
                assertEquals(this.contentSize, contentSize)
                assertEquals(this.editTime, editTime)
                assertEquals(this.ephemeral, ephemeral)
                assertEquals(this.expiryTime, expiryTime)
                assertEquals(this.expired, expired)
                assertEquals(this.duration, duration)
                assertEquals(this.quote, quote)
                assertEquals(this.quoteValidity, quoteValidity)
                assertEquals(this.forceReadReceipts, forceReadReceipts)
                assertEquals(this.assetId, assetId)
            }
        }
    }

    @Test
    fun givenMessageDeletionInsertedIntoMessageDeletionTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val messageId = "testMessageId"
        val timestamp = 1584710479

        MessageDeletionTableTestHelper.insertMessageDeletion(
            messageId = messageId,
            timestamp = timestamp,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allMessageDeletions()[0]) {
                assertEquals(this.messageId, messageId)
                assertEquals(this.timestamp, timestamp)
            }
        }
    }

    @Test
    fun givenLikeInsertedIntoLikesTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

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

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allLikes()[0]) {
                assertEquals(this.messageId, messageId)
                assertEquals(this.userId, userId)
                assertEquals(this.timeStamp, timestamp)
                assertEquals(this.action, action)
            }
        }
    }

    @Test
    fun givenMessageContentIndexInsertedIntoMessageContentIndexTableVersion126_whenMigratedToVersion128_thenAssertDataIsStillIntact() {

        val messageId = "testMessageId"
        val conversationId = "testConversationId"
        val content = "content"
        val timestamp = 1584710479

        MessageContentIndexTableTestHelper.insertMessageContentIndex(
            messageId = messageId,
            conversationId = conversationId,
            content = content,
            timestamp = timestamp,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allMessageContentIndexes()[0]) {
                assertEquals(this.messageId, messageId)
                assertEquals(this.convId, conversationId)
                assertEquals(this.content, content)
                assertEquals(this.timestamp, timestamp)
            }
        }
    }

    private suspend fun allMessages() =
        getDatabase().messagesDao().allMessages()

    private suspend fun allMessageDeletions() =
        getDatabase().messagesDeletionDao().allMessageDeletions()

    private suspend fun allLikes() = getDatabase().likesDao().allLikes()

    private suspend fun allMessageContentIndexes() =
        getDatabase().messageContentIndexDao().allMessageContentIndexes()
}
