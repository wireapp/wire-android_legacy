package com.waz.zclient.feature.backup.receipts

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.data.receipts.ReadReceiptsTestDataProvider
import com.waz.zclient.storage.db.receipts.ReadReceiptsEntity
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class ReadReceiptsBackupMapperTest : UnitTest() {
    private lateinit var backupMapper: ReadReceiptsBackupMapper

    @Before
    fun setup() {
        backupMapper = ReadReceiptsBackupMapper()
    }

    @Test
    fun `given a ReadReceiptsEntity, when fromEntity() is called, then maps it into a ReadReceiptsBackUpModel`() {
        val data = ReadReceiptsTestDataProvider.provideDummyTestData()

        val entity = ReadReceiptsEntity(
            messageId = data.messageId,
            userId = data.userId,
            timestamp = data.timestamp
        )

        val model = backupMapper.fromEntity(entity)

        assertEquals(data.messageId, model.messageId)
        assertEquals(data.userId, model.userId)
        assertEquals(data.timestamp, model.timestamp)
    }

    @Test
    fun `given a ReadReceiptsBackUpModel, when toEntity() is called, then maps it into a ReadReceiptsEntity`() {
        val data = ReadReceiptsTestDataProvider.provideDummyTestData()

        val model = ReadReceiptsBackUpModel(
            messageId = data.messageId,
            userId = data.userId,
            timestamp = data.timestamp
        )

        val entity = backupMapper.toEntity(model)

        assertEquals(data.messageId, entity.messageId)
        assertEquals(data.userId, entity.userId)
        assertEquals(data.timestamp, entity.timestamp)
    }
}
