package com.waz.zclient.storage.userdatabase.conversations


import androidx.room.Room
import com.waz.zclient.framework.data.conversations.ConversationFoldersTestDataProvider
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.conversations.ConversationMembersEntity
import com.waz.zclient.storage.db.conversations.ConversationMembersDao
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ConversationMembersDaoTest : IntegrationTest() {

    private lateinit var conversationMembersDao: ConversationMembersDao

    private lateinit var userDatabase: UserDatabase

    @Before
    fun setup() {
        userDatabase = Room.inMemoryDatabaseBuilder(
                getApplicationContext(),
                UserDatabase::class.java
        ).build()
        conversationMembersDao = userDatabase.conversationMembersDao()
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
            conversationMembersDao.insert(
                ConversationMembersEntity(
                    userId = UUID.randomUUID().toString(),
                    conversationId = UUID.randomUUID().toString(),
                    role = "admin"
                )
            )
        }
        val storedConversationMembers = conversationMembersDao.allConversationMembers()
        assertEquals(storedConversationMembers.size, numberOfItems)
    }
    
    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs0_thenAssert5ItemIsCollectedAndSizeIs5(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = conversationMembersDao.nextBatch(0, 5)

        assertEquals(storedValues?.size, 5)
        assertEquals(conversationMembersDao.count(), 10)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs5_thenAssert5ItemIsCollectedAndSizeIs10(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = conversationMembersDao.nextBatch(5, 5)
        assertEquals(storedValues?.size, 5)
        assertEquals(conversationMembersDao.count(), 10)
    }

    private suspend fun insertRandomItems(numberOfItems: Int) {
        repeat(numberOfItems) {
            val normalEntity = conversationMembersEntity()
            conversationMembersDao.insert(normalEntity)
        }
    }

    private fun conversationMembersEntity(id: String = UUID.randomUUID().toString()): ConversationMembersEntity =
        ConversationMembersEntity(
            userId = id,
            conversationId = UUID.randomUUID().toString(),
            role = "admin"
        )
}
