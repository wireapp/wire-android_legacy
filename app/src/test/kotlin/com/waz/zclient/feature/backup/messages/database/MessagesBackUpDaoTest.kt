package com.waz.zclient.feature.backup.messages.database

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.eq
import com.waz.zclient.storage.db.messages.MessagesDao
import com.waz.zclient.storage.db.messages.MessagesEntity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*

class MessagesBackUpDaoTest : UnitTest() {

    private lateinit var messagesBackUpDao: MessagesBackUpDao

    @Mock
    private lateinit var messagesDao: MessagesDao

    @Before
    fun setup() {
        messagesBackUpDao = MessagesBackUpDao(messagesDao)
    }

    @Test
    fun `when count is called, then verify size is called`(): Unit = runBlocking {
        `when`(messagesDao.size()).thenReturn(any())

        messagesBackUpDao.count()

        verify(messagesDao).size()
    }

    @Test
    fun `given start of 0 and backSize of 5, when dao batch is called, then return batch of items`(): Unit = runBlocking {
        val start = 0
        val batchSize = 5

        messagesBackUpDao.nextBatch(start, batchSize)

        verify(messagesDao).batch(eq(start), eq(batchSize))
    }

    @Test
    fun `given entity, when insert is called, then insert same entity`(): Unit = runBlocking {
        val entity = mock(MessagesEntity::class.java)

        messagesBackUpDao.insert(entity)

        verify(messagesDao).insert(eq(entity))
    }

}
