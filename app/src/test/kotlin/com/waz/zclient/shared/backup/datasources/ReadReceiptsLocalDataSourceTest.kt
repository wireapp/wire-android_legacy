package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.ReadReceiptsJSONEntity
import com.waz.zclient.shared.backup.datasources.local.ReadReceiptsLocalDataSource
import com.waz.zclient.storage.db.receipts.ReadReceiptsDao
import com.waz.zclient.storage.db.receipts.ReadReceiptsEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

@ExperimentalCoroutinesApi
class ReadReceiptsLocalDataSourceTest : UnitTest() {

    private val readReceiptsEntity = ReadReceiptsEntity(
        messageId = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        userId = "2f9e89c9-78a7-477d-8def-fbd7ca3846b5",
        timestamp = 0
    )

    @Mock
    private lateinit var readReceiptsDao: ReadReceiptsDao
    private lateinit var dataSource: ReadReceiptsLocalDataSource

    @Before
    fun setup() {
        dataSource = ReadReceiptsLocalDataSource(readReceiptsDao)
    }

    @Test
    fun `convert a read receipts entity to a json entity and back`() = run {
        val readReceiptsJSONEntity = ReadReceiptsJSONEntity.from(readReceiptsEntity)
        val result: ReadReceiptsEntity = readReceiptsJSONEntity.toEntity()

        result shouldEqual readReceiptsEntity
    }

    @Test
    fun `convert a read receipts entity to json string and back`(): Unit = run {
        val jsonStr = dataSource.serialize(readReceiptsEntity)
        val result = dataSource.deserialize(jsonStr)
        result shouldEqual readReceiptsEntity
    }
}