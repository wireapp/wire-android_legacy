package com.waz.zclient.storage.userdatabase.conversations

import androidx.room.Room
import com.waz.zclient.framework.data.conversations.ConversationRolesTestDataProvider
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.conversations.ConversationRoleActionDao
import com.waz.zclient.storage.db.conversations.ConversationRoleActionEntity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class ConversationRoleActionDaoTest : IntegrationTest() {

    private lateinit var convRoleActionDao: ConversationRoleActionDao

    private lateinit var userDatabase: UserDatabase

    @Before
    fun setup() {
        userDatabase = Room.inMemoryDatabaseBuilder(
            getApplicationContext(),
            UserDatabase::class.java
        ).build()
        convRoleActionDao = userDatabase.conversationRoleActionDao()
    }

    @After
    fun tearDown() {
        userDatabase.close()
    }

    @Test
    fun givenAListOfEntries_whenAllConversationRolesIsCalled_thenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        val data = ConversationRolesTestDataProvider.data()
        convRoleActionDao.insert(ConversationRoleActionEntity(data.label, data.action, data.convId))

        val storedMessages = convRoleActionDao.allConversationRoleActions()

        assertEquals(storedMessages.first().action, data.action)
        assertEquals(storedMessages.first().label, data.label)
        assertEquals(storedMessages.first().convId, data.convId)
    }
}
