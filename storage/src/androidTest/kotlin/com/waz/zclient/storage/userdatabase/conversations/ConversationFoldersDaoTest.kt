package com.waz.zclient.storage.userdatabase.conversations

import androidx.room.Room
import com.waz.zclient.framework.data.conversations.ConversationFoldersTestDataProvider
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.conversations.ConversationFoldersDao
import com.waz.zclient.storage.db.conversations.ConversationFoldersEntity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ConversationFoldersDaoTest : IntegrationTest() {

    private lateinit var conversationFoldersDao: ConversationFoldersDao

    private lateinit var userDatabase: UserDatabase

    @Before
    fun setup() {
        userDatabase = Room.inMemoryDatabaseBuilder(
            getApplicationContext(),
            UserDatabase::class.java
        ).build()
        conversationFoldersDao = userDatabase.conversationFoldersDao()
    }

    @After
    fun tearDown() {
        userDatabase.close()
    }

    @Test
    fun givenAListOfEntries_whenAllConversationsFoldersIsCalled_thenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        val numberOfItems = 3
        val data = ConversationFoldersTestDataProvider.listOfData(numberOfItems)
        repeat(data.size) {
            conversationFoldersDao.insert(
                ConversationFoldersEntity(
                    folderId = UUID.randomUUID().toString(),
                    convId = UUID.randomUUID().toString()
                )
            )
        }
        val storedMessages = conversationFoldersDao.allConversationFolders()
        assertEquals(storedMessages.size, numberOfItems)
    }


    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs0_thenAssert5ItemIsCollectedAndSizeIs5(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = conversationFoldersDao.nextBatch(0, 5)

        assertEquals(storedValues?.size, 5)
        assertEquals(conversationFoldersDao.count(), 10)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs5_thenAssert5ItemIsCollectedAndSizeIs10(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = conversationFoldersDao.nextBatch(5, 5)
        assertEquals(storedValues?.size, 5)
        assertEquals(conversationFoldersDao.count(), 10)
    }

    private suspend fun insertRandomItems(numberOfItems: Int) {
        repeat(numberOfItems) {
            val normalEntity = conversationEntity()
            conversationFoldersDao.insert(normalEntity)
        }
    }

    private fun conversationEntity(id: String = UUID.randomUUID().toString()): ConversationFoldersEntity =
        ConversationFoldersEntity(
            convId = id,
            folderId = UUID.randomUUID().toString()
        )
}
