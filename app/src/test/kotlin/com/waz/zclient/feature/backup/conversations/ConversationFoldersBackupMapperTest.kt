package com.waz.zclient.feature.backup.conversations

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.data.conversations.ConversationFoldersTestDataProvider
import com.waz.zclient.storage.db.conversations.ConversationFoldersEntity
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class ConversationFoldersBackupMapperTest : UnitTest() {

    private lateinit var backupMapper: ConversationFoldersBackupMapper

    @Before
    fun setup() {
        backupMapper = ConversationFoldersBackupMapper()
    }

    @Test
    fun `given a ConversationFoldersEntity, when fromEntity() is called, then maps it into a ConversationFoldersBackUpModel`() {
        val data = ConversationFoldersTestDataProvider.provideDummyTestData()

        val entity = ConversationFoldersEntity(
            convId = data.convId,
            folderId = data.folderId
        )

        val model = backupMapper.fromEntity(entity)

        assertEquals(data.convId, model.convId)
        assertEquals(data.folderId, model.folderId)
    }

    @Test
    fun `given a ConversationFoldersBackUpModel, when toEntity() is called, then maps it into a ConversationFoldersEntity`() {
        val data = ConversationFoldersTestDataProvider.provideDummyTestData()

        val model = ConversationFoldersBackUpModel(
            convId = data.convId,
            folderId = data.folderId
        )

        val entity = backupMapper.toEntity(model)

        assertEquals(data.convId, entity.convId)
        assertEquals(data.folderId, entity.folderId)
    }
}
