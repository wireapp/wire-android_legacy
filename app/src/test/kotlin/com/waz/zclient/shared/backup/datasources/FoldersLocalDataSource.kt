package com.waz.zclient.shared.backup.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.shared.backup.datasources.local.FoldersJSONEntity
import com.waz.zclient.shared.backup.datasources.local.FoldersLocalDataSource
import com.waz.zclient.storage.db.folders.FoldersDao
import com.waz.zclient.storage.db.folders.FoldersEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

@ExperimentalCoroutinesApi
class FoldersLocalDataSourceTest : UnitTest() {

    private val foldersEntity = FoldersEntity(
        id = "3-1-70b5baab-323d-446e-936d-745c64d6c7d8",
        name = "folder",
        type = 0
    )

    @Mock
    private lateinit var foldersDao: FoldersDao
    private lateinit var dataSource: FoldersLocalDataSource

    @Before
    fun setup() {
        dataSource = FoldersLocalDataSource(foldersDao)
    }

    @Test
    fun `convert a folders entity to a json entity and back`() = run {
        val foldersJSONEntity = FoldersJSONEntity.from(foldersEntity)
        val result: FoldersEntity = foldersJSONEntity.toEntity()

        result shouldEqual foldersEntity
    }

    @Test
    fun `convert a folders entity to json string and back`(): Unit = run {
        val jsonStr = dataSource.serialize(foldersEntity)
        val result = dataSource.deserialize(jsonStr)
        result shouldEqual foldersEntity
    }
}