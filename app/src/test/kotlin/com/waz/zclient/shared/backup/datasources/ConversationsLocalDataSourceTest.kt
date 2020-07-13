package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.ConversationsJSONEntity
import com.waz.zclient.shared.backup.datasources.local.ConversationsLocalDataSource
import com.waz.zclient.storage.db.conversations.ConversationsDao
import com.waz.zclient.storage.db.conversations.ConversationsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

@ExperimentalCoroutinesApi
class ConversationsLocalDataSourceTest : UnitTest() {

    private val conversationsEntity = ConversationsEntity(
        id = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        remoteId = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        name = null,
        creator = "3762d820-83a1-4fae-ae58-6c39fb2e9d8a",
        conversationType = 0,
        team = null,
        managed = false,
        lastEventTime = 0,
        active = true,
        lastRead = 0,
        mutedStatus = 0,
        muteTime = 0,
        archived = false,
        archiveTime = 0,
        cleared = null,
        generatedName = "",
        searchKey = null,
        unreadCount = 0,
        unsentCount = 0,
        hidden = false,
        missedCall = null,
        incomingKnock = null,
        verified = null,
        ephemeral = null,
        globalEphemeral = null,
        unreadCallCount = 0,
        unreadPingCount = 0,
        access = null,
        accessRole = null,
        link = null,
        unreadMentionsCount = 0,
        unreadQuoteCount = 0,
        receiptMode = null
    )

    @Mock
    private lateinit var conversationsDao: ConversationsDao
    private lateinit var dataSource: ConversationsLocalDataSource

    @Before
    fun setup() {
        dataSource = ConversationsLocalDataSource(conversationsDao)
    }

    @Test
    fun `convert a conversations entity to a json entity and back`() = run {
        val conversationsJSONEntity = ConversationsJSONEntity.from(conversationsEntity)
        val result: ConversationsEntity = conversationsJSONEntity.toEntity()

        result shouldEqual conversationsEntity
    }

    @Test
    fun `convert a conversations entity to json string and back`(): Unit = run {
        val jsonStr = dataSource.serialize(conversationsEntity)
        val result = dataSource.deserialize(jsonStr)

        result shouldEqual conversationsEntity
    }
}