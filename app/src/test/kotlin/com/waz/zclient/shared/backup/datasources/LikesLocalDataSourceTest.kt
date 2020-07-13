package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.LikesJSONEntity
import com.waz.zclient.shared.backup.datasources.local.LikesLocalDataSource
import com.waz.zclient.storage.db.messages.LikesDao
import com.waz.zclient.storage.db.messages.LikesEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

@ExperimentalCoroutinesApi
class LikesLocalDataSourceTest : UnitTest() {

    private val likesEntity = LikesEntity(
        messageId = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        userId = "2f9e89c9-78a7-477d-8def-fbd7ca3846b5",
        timeStamp = 0,
        action = 0
    )

    @Mock
    private lateinit var likesDao: LikesDao
    private lateinit var dataSource: LikesLocalDataSource

    @Before
    fun setup() {
        dataSource = LikesLocalDataSource(likesDao)
    }

    @Test
    fun `convert a likes entity to a json entity and back`() = run {
        val likesJSONEntity = LikesJSONEntity.from(likesEntity)
        val result: LikesEntity = likesJSONEntity.toEntity()

        result shouldEqual likesEntity
    }

    @Test
    fun `convert a likes entity to json string and back`(): Unit = run {
        val jsonStr = dataSource.serialize(likesEntity)
        val result = dataSource.deserialize(jsonStr)
        result shouldEqual likesEntity
    }
}