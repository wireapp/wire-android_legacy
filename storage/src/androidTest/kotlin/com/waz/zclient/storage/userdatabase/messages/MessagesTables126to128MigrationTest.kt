package com.waz.zclient.storage.userdatabase.messages

import com.waz.zclient.framework.data.messages.MessagesTestDataProvider
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
        val data = MessagesTestDataProvider.provideDummyTestData()
        MessagesTableTestHelper.insertMessage(
            id = data.id,
            conversationId = data.conversationId,
            messageType = data.messageType,
            userId = data.userId,
            clientId = data.clientId,
            content = data.content,
            protos = data.protos,
            time = data.time,
            localTime = data.localTime,
            firstMessage = data.firstMessage,
            members = data.members,
            recipient = data.recipient,
            email = data.email,
            name = data.name,
            messageState = data.messageState,
            contentSize = data.contentSize,
            editTime = data.editTime,
            ephemeral = data.ephemeral,
            expiryTime = data.time,
            expired = data.expired,
            duration = data.duration,
            quote = data.quote,
            quoteValidity = data.quoteValidity,
            forceReadReceipts = data.forceReadReceipts,
            assetId = data.assetId,
            openHelper = testOpenHelper
        )

        validateMigration(USER_DATABASE_MIGRATION_126_TO_127, USER_DATABASE_MIGRATION_127_TO_128)

        runBlocking {
            with(allMessages()[0]) {
                assertEquals(this.id, data.id)
                assertEquals(this.conversationId, data.conversationId)
                assertEquals(this.messageType, data.messageType)
                assertEquals(this.userId, data.userId)
                assertEquals(this.content, data.content)
                assertTrue(this.protos!!.contentEquals(data.protos!!))
                assertEquals(this.time, data.time)
                assertEquals(firstMessage, data.firstMessage)
                assertEquals(this.members, data.members)
                assertEquals(this.recipient, data.recipient)
                assertEquals(this.email, data.email)
                assertEquals(this.name, data.name)
                assertEquals(this.messageState, data.messageState)
                assertEquals(this.contentSize, data.contentSize)
                assertEquals(this.editTime, data.editTime)
                assertEquals(this.ephemeral, data.ephemeral)
                assertEquals(this.expiryTime, data.expiryTime)
                assertEquals(this.expired, data.expired)
                assertEquals(this.duration, data.duration)
                assertEquals(this.quote, data.quote)
                assertEquals(this.quoteValidity, data.quoteValidity)
                assertEquals(this.forceReadReceipts, data.forceReadReceipts)
                assertEquals(this.assetId, data.assetId)
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
