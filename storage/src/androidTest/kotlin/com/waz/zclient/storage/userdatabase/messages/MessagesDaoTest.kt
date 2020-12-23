package com.waz.zclient.storage.userdatabase.messages

import androidx.room.Room
import com.waz.zclient.framework.data.messages.MessagesTestDataProvider
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.messages.MessagesDao
import com.waz.zclient.storage.db.messages.MessagesEntity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*

class MessagesDaoTest : IntegrationTest() {

    private lateinit var messagesDao: MessagesDao

    private lateinit var userDatabase: UserDatabase

    @Before
    fun setup() {
        userDatabase = Room.inMemoryDatabaseBuilder(
            getApplicationContext(),
            UserDatabase::class.java
        ).build()
        messagesDao = userDatabase.messagesDao()
    }

    @After
    fun tearDown() {
        userDatabase.close()
    }

    @Test
    fun givenAListOfEntries_whenAllMessagesIsCalled_thenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        listOfMessages().forEach {
            messagesDao.insert(it)
        }
        val storedMessages = messagesDao.allMessages()

        assertEquals(storedMessages.first().id, TEST_MESSAGES_ENTITY_PRIMARY_ID)
        assertEquals(storedMessages.last().id, TEST_MESSAGES_ENTITY_SECONDARY_ID)
        assertEquals(storedMessages.size, 2)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs0_thenAssert5ItemIsCollectedAndSizeIs5(): Unit = runBlocking {
        insertRandomItems(10)
        val storedMessages = messagesDao.nextBatch(0, 5)

        assertEquals(storedMessages?.size, 5)
        assertEquals(messagesDao.count(), 10)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs5_thenAssert5ItemIsCollectedAndSizeIs10(): Unit = runBlocking {
        insertRandomItems(10)

        val storedMessages = messagesDao.nextBatch(5, 5)
        assertEquals(storedMessages?.size, 5)
        assertEquals(messagesDao.count(), 10)
    }

    private fun messagesEntity(messageKey: String): MessagesEntity {
        val data = MessagesTestDataProvider.provideDummyTestData()
        return MessagesEntity(
            id = messageKey,
            conversationId = data.conversationId,
            messageType = data.messageType,
            userId = data.userId,
            clientId = data.clientId,
            errorCode = data.errorCode,
            content = data.content,
            protos = data.protos,
            time = data.time,
            firstMessage = data.firstMessage,
            members = data.members,
            recipient = data.recipient,
            email = data.email,
            name = data.name,
            messageState = data.messageState,
            editTime = data.editTime,
            localTime = data.localTime,
            expiryTime = data.expiryTime,
            contentSize = data.contentSize,
            ephemeral = data.ephemeral,
            expired = data.expired,
            duration = data.duration,
            quote = data.quote,
            quoteValidity = data.quoteValidity,
            forceReadReceipts = data.forceReadReceipts,
            assetId = data.assetId
        )
    }

    private suspend fun insertRandomItems(numberOfItems: Int) {
        repeat(numberOfItems) {
            val normalEntity = messagesEntity(UUID.randomUUID().toString())
            messagesDao.insert(normalEntity)
        }
    }

    private fun listOfMessages() = listOf(
        messagesEntity(TEST_MESSAGES_ENTITY_PRIMARY_ID),
        messagesEntity(TEST_MESSAGES_ENTITY_SECONDARY_ID)
    )

    companion object {
        private const val TEST_MESSAGES_ENTITY_PRIMARY_ID = "1"
        private const val TEST_MESSAGES_ENTITY_SECONDARY_ID = "2"
        private const val TEST_TENTH_ITEM_ID = "Item 10"
        private const val TEST_FIFTH_ITEM_ID = "Item 5"
    }
}
