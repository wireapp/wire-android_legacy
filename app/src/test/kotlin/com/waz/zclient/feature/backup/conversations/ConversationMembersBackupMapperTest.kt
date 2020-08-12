package com.waz.zclient.feature.backup.conversations

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.data.conversations.ConversationMembersTestDataProvider
import com.waz.zclient.storage.db.conversations.ConversationMembersEntity
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class ConversationMembersBackupMapperTest : UnitTest() {

    private lateinit var backupMapper: ConversationMembersBackupMapper

    @Before
    fun setup() {
        backupMapper = ConversationMembersBackupMapper()
    }

    @Test
    fun `given a ConversationMembersEntity, when fromEntity() is called, then maps it into a ConversationMembersBackUpModel`() {
        val data = ConversationMembersTestDataProvider.provideDummyTestData()

        val entity = ConversationMembersEntity(
            userId = data.userId,
            conversationId = data.conversationId,
            role = data.role
        )

        val model = backupMapper.fromEntity(entity)

        assertEquals(data.userId, model.userId)
        assertEquals(data.conversationId, model.conversationId)
        assertEquals(data.role, model.role)
    }

    @Test
    fun `given a ConversationMembersBackUpModel, when toEntity() is called, then maps it into a ConversationMembersEntity`() {
        val data = ConversationMembersTestDataProvider.provideDummyTestData()

        val model = ConversationMembersBackUpModel(
            userId = data.userId,
            conversationId = data.conversationId,
            role = data.role
        )

        val entity = backupMapper.toEntity(model)

        assertEquals(data.userId, entity.userId)
        assertEquals(data.conversationId, entity.conversationId)
        assertEquals(data.role, entity.role)
    }
}
