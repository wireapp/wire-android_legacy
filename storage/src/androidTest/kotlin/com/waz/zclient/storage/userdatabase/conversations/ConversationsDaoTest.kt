package com.waz.zclient.storage.userdatabase.conversations

import androidx.room.Room
import com.waz.zclient.framework.data.conversations.ConversationsTestDataProvider
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.conversations.ConversationsDao
import com.waz.zclient.storage.db.conversations.ConversationsEntity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*

class ConversationsDaoTest : IntegrationTest() {

    private lateinit var conversationsDao: ConversationsDao

    private lateinit var userDatabase: UserDatabase

    @Before
    fun setup() {
        userDatabase = Room.inMemoryDatabaseBuilder(
            getApplicationContext(),
            UserDatabase::class.java
        ).build()
        conversationsDao = userDatabase.conversationsDao()
    }

    @After
    fun tearDown() {
        userDatabase.close()
    }

    @Test
    fun givenAListOfEntries_whenAllConversationsIsCalled_thenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        val numberOfItems = 3
        val data = ConversationsTestDataProvider.listOfData(numberOfItems)
        data.forEach {
            conversationsDao.insert(
                ConversationsEntity(
                    id = UUID.randomUUID().toString(),
                    remoteId = it.remoteId,
                    name = it.name,
                    creator = it.creator,
                    conversationType = it.conversationType,
                    team = it.team,
                    managed = it.managed,
                    lastEventTime = it.lastEventTime,
                    active = it.active,
                    lastRead = it.lastRead,
                    mutedStatus = it.mutedStatus,
                    muteTime = it.muteTime,
                    archived = it.archived,
                    archiveTime = it.archiveTime,
                    cleared = it.cleared,
                    generatedName = it.generatedName,
                    searchKey = it.searchKey,
                    unreadCount = it.unreadCount,
                    unsentCount = it.unsentCount,
                    hidden = it.hidden,
                    missedCall = it.missedCall,
                    incomingKnock = it.incomingKnock,
                    verified = it.verified,
                    ephemeral = it.ephemeral,
                    globalEphemeral = it.globalEphemeral,
                    unreadCallCount = it.unreadCallCount,
                    unreadPingCount = it.unreadPingCount,
                    access = it.access,
                    accessRole = it.accessRole,
                    link = it.link,
                    unreadMentionsCount = it.unreadMentionsCount,
                    unreadQuoteCount = it.unreadQuoteCount,
                    receiptMode = it.receiptMode,
                    legalHoldStatus = it.legalHoldStatus,
                    domain = it.domain
                )
            )
        }
        val storedMessages = conversationsDao.allConversations()
        assertEquals(storedMessages.first().name, data.first().name)
        assertEquals(storedMessages.last().name, data.last().name)
        assertEquals(storedMessages.size, numberOfItems)
    }


    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs0_thenAssert5ItemIsCollectedAndSizeIs5(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = conversationsDao.nextBatch(0, 5)

        assertEquals(storedValues?.size, 5)
        assertEquals(conversationsDao.count(), 10)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs5_thenAssert5ItemIsCollectedAndSizeIs10(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = conversationsDao.nextBatch(5, 5)
        assertEquals(storedValues?.size, 5)
        assertEquals(conversationsDao.count(), 10)
    }

    private suspend fun insertRandomItems(numberOfItems: Int) {
        repeat(numberOfItems) {
            val normalEntity = conversationEntity()
            conversationsDao.insert(normalEntity)
        }
    }

    private fun conversationEntity(id: String = UUID.randomUUID().toString()): ConversationsEntity {
        val data = ConversationsTestDataProvider.provideDummyTestData()
        return ConversationsEntity(
            id = id,
            remoteId = data.remoteId,
            name = data.name,
            creator = data.creator,
            conversationType = data.conversationType,
            team = data.team,
            managed = data.managed,
            lastEventTime = data.lastEventTime,
            active = data.active,
            lastRead = data.lastRead,
            mutedStatus = data.mutedStatus,
            muteTime = data.muteTime,
            archived = data.archived,
            archiveTime = data.archiveTime,
            cleared = data.cleared,
            generatedName = data.generatedName,
            searchKey = data.searchKey,
            unreadCount = data.unreadCount,
            unsentCount = data.unsentCount,
            hidden = data.hidden,
            missedCall = data.missedCall,
            incomingKnock = data.incomingKnock,
            verified = data.verified,
            ephemeral = data.ephemeral,
            globalEphemeral = data.globalEphemeral,
            unreadCallCount = data.unreadCallCount,
            unreadPingCount = data.unreadPingCount,
            access = data.access,
            accessRole = data.accessRole,
            link = data.link,
            unreadMentionsCount = data.unreadMentionsCount,
            unreadQuoteCount = data.unreadQuoteCount,
            receiptMode = data.receiptMode,
            legalHoldStatus = data.legalHoldStatus,
            domain = data.domain
        )
    }

}
