package com.waz.zclient.feature.backup.buttons

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.data.buttons.ButtonsTestDataProvider
import com.waz.zclient.storage.db.buttons.ButtonsEntity
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class ButtonsBackupMapperTest : UnitTest() {
    private lateinit var backupMapper: ButtonsBackupMapper

    @Before
    fun setup() {
        backupMapper = ButtonsBackupMapper()
    }

    @Test
    fun `given a ButtonEntity, when fromEntity() is called, then maps it into a ButtonBackUpModel`() {
        val data = ButtonsTestDataProvider.provideDummyTestData()

        val entity = ButtonsEntity(
                messageId = data.messageId,
                buttonId = data.buttonId,
                title = data.title,
                ordinal = data.ordinal,
                state = data.state
        )

        val model = backupMapper.fromEntity(entity)

        assertEquals(data.messageId, model.messageId)
        assertEquals(data.buttonId, model.buttonId)
        assertEquals(data.title, model.title)
        assertEquals(data.ordinal, model.ordinal)
        assertEquals(data.state, model.state)
    }

    @Test
    fun `given a ButtonBackUpModel, when toEntity() is called, then maps it into a ButtonEntity`() {
        val data = ButtonsTestDataProvider.provideDummyTestData()

        val model = ButtonsBackUpModel(
            messageId = data.messageId,
            buttonId = data.buttonId,
            title = data.title,
            ordinal = data.ordinal,
            state = data.state
        )

        val entity = backupMapper.toEntity(model)

        assertEquals(data.messageId, entity.messageId)
        assertEquals(data.buttonId, entity.buttonId)
        assertEquals(data.title, entity.title)
        assertEquals(data.ordinal, entity.ordinal)
        assertEquals(data.state, entity.state)
    }
}
