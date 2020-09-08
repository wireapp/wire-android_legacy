package com.waz.zclient.feature.backup.messages

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.data.messages.LikesTestDataProvider
import com.waz.zclient.storage.db.messages.LikesEntity
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class LikesBackupMapperTest : UnitTest() {
    private lateinit var backupMapper: LikesBackupMapper

    @Before
    fun setup() {
        backupMapper = LikesBackupMapper()
    }

    @Test
    fun `given a LikesEntity, when fromEntity() is called, then maps it into a LikesBackUpModel`() {
        val data = LikesTestDataProvider.provideDummyTestData()

        val entity = LikesEntity(
            messageId = data.messageId,
            userId = data.userId,
            timeStamp = data.timeStamp,
            action = data.action
        )

        val model = backupMapper.fromEntity(entity)

        assertEquals(data.messageId, model.messageId)
        assertEquals(data.userId, model.userId)
        assertEquals(data.timeStamp, model.timeStamp)
        assertEquals(data.action, model.action)
    }

    @Test
    fun `given a LikesBackUpModel, when toEntity() is called, then maps it into a LikesEntity`() {
        val data = LikesTestDataProvider.provideDummyTestData()

        val model = LikesBackUpModel(
                messageId = data.messageId,
                userId = data.userId,
                timeStamp = data.timeStamp,
                action = data.action
        )

        val entity = backupMapper.toEntity(model)

        assertEquals(data.messageId, entity.messageId)
        assertEquals(data.userId, entity.userId)
        assertEquals(data.timeStamp, entity.timeStamp)
        assertEquals(data.action, entity.action)
    }
}
