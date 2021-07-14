package com.waz.zclient.feature.backup.conversations

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.data.conversations.ConversationsTestDataProvider
import com.waz.zclient.storage.db.conversations.ConversationsEntity
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class ConversationBackupMapperTest : UnitTest() {
    private lateinit var backupMapper: ConversationsBackupMapper

    @Before
    fun setup() {
        backupMapper = ConversationsBackupMapper()
    }

    @Test
    fun `given a ConversationsEntity, when fromEntity() is called, then maps it into a ConversationsBackUpModel`() {
        val data = ConversationsTestDataProvider.provideDummyTestData()

        val entity = ConversationsEntity(
            id = data.id,
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

        val model = backupMapper.fromEntity(entity)

        assertEquals(data.id, model.id)
        assertEquals(data.remoteId, model.remoteId)
        assertEquals(data.name, model.name)
        assertEquals(data.creator, model.creator)
        assertEquals(data.conversationType, model.conversationType)
        assertEquals(data.team, model.team)
        assertEquals(data.managed, model.managed)
        assertEquals(data.lastEventTime, model.lastEventTime)
        assertEquals(data.active, model.active)
        assertEquals(data.lastRead, model.lastRead)
        assertEquals(data.mutedStatus, model.mutedStatus)
        assertEquals(data.cleared, model.cleared)
        assertEquals(data.generatedName, model.generatedName)
        assertEquals(data.searchKey, model.searchKey)
        assertEquals(data.unreadCount, model.unreadCount)
        assertEquals(data.unsentCount, model.unsentCount)
        assertEquals(data.hidden, model.hidden)
        assertEquals(data.missedCall, model.missedCall)
        assertEquals(data.incomingKnock, model.incomingKnock)
        assertEquals(data.verified, model.verified)
        assertEquals(data.ephemeral, model.ephemeral)
        assertEquals(data.globalEphemeral, model.globalEphemeral)
        assertEquals(data.unreadCallCount, model.unreadCallCount)
        assertEquals(data.unreadPingCount, model.unreadPingCount)
        assertEquals(data.access, model.access)
        assertEquals(data.accessRole, model.accessRole)
        assertEquals(data.link, model.link)
        assertEquals(data.unreadMentionsCount, model.unreadMentionsCount)
        assertEquals(data.unreadQuoteCount, model.unreadQuoteCount)
        assertEquals(data.receiptMode, model.receiptMode)
    }

    @Test
    fun `given a ConversationsBackUpModel, when toEntity() is called, then maps it into a ConversationsEntity`() {
        val data = ConversationsTestDataProvider.provideDummyTestData()

        val model = ConversationsBackUpModel(
            id = data.id,
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

        val entity = backupMapper.toEntity(model)

        assertEquals(data.id, entity.id)
        assertEquals(data.remoteId, entity.remoteId)
        assertEquals(data.name, entity.name)
        assertEquals(data.creator, entity.creator)
        assertEquals(data.conversationType, entity.conversationType)
        assertEquals(data.team, entity.team)
        assertEquals(data.managed, entity.managed)
        assertEquals(data.lastEventTime, entity.lastEventTime)
        assertEquals(data.active, entity.active)
        assertEquals(data.lastRead, entity.lastRead)
        assertEquals(data.mutedStatus, entity.mutedStatus)
        assertEquals(data.cleared, entity.cleared)
        assertEquals(data.generatedName, entity.generatedName)
        assertEquals(data.searchKey, entity.searchKey)
        assertEquals(data.unreadCount, entity.unreadCount)
        assertEquals(data.unsentCount, entity.unsentCount)
        assertEquals(data.hidden, entity.hidden)
        assertEquals(data.missedCall, entity.missedCall)
        assertEquals(data.incomingKnock, entity.incomingKnock)
        assertEquals(data.verified, entity.verified)
        assertEquals(data.ephemeral, entity.ephemeral)
        assertEquals(data.globalEphemeral, entity.globalEphemeral)
        assertEquals(data.unreadCallCount, entity.unreadCallCount)
        assertEquals(data.unreadPingCount, entity.unreadPingCount)
        assertEquals(data.access, entity.access)
        assertEquals(data.accessRole, entity.accessRole)
        assertEquals(data.link, entity.link)
        assertEquals(data.unreadMentionsCount, entity.unreadMentionsCount)
        assertEquals(data.unreadQuoteCount, entity.unreadQuoteCount)
        assertEquals(data.receiptMode, entity.receiptMode)
    }
}
