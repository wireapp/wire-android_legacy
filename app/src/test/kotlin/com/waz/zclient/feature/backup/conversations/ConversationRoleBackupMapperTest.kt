package com.waz.zclient.feature.backup.conversations

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.data.conversations.ConversationRolesTestDataProvider
import com.waz.zclient.storage.db.conversations.ConversationRoleActionEntity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConversationRoleBackupMapperTest : UnitTest() {

    private lateinit var conversationRolesBackupMapper: ConversationRoleBackupMapper

    @Before
    fun setUp() {
        conversationRolesBackupMapper = ConversationRoleBackupMapper()
    }

    @Test
    fun `given a ConversationRoleActionEntity, when fromEntity() is called, then maps it into a ConversationRoleActionBackUpModel`() {
        val data = ConversationRolesTestDataProvider.provideDummyTestData()

        val entity = ConversationRoleActionEntity(label = data.label, action = data.action, convId = data.convId)

        val model = conversationRolesBackupMapper.fromEntity(entity)

        assertEquals(data.label, model.label)
        assertEquals(data.action, model.action)
        assertEquals(data.convId, model.convId)
    }

    @Test
    fun `given a ConversationRoleActionBackUpModel, when toEntity() is called, then maps it into a ConversationRoleActionEntity`() {
        val data = ConversationRolesTestDataProvider.provideDummyTestData()

        val model = ConversationRoleActionBackUpModel(label = data.label, action = data.action, convId = data.convId)

        val entity = conversationRolesBackupMapper.toEntity(model)

        assertEquals(data.label, entity.label)
        assertEquals(data.action, entity.action)
        assertEquals(data.convId, entity.convId)
    }
}
