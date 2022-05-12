package com.waz.zclient.feature.backup.folders

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.data.folders.FoldersTestDataProvider
import com.waz.zclient.storage.db.folders.FoldersEntity
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class FoldersBackupMapperTest : UnitTest() {

    private lateinit var backupMapper: FoldersBackupMapper

    @Before
    fun setUp() {
        backupMapper = FoldersBackupMapper()
    }

    @Test
    fun `given a FoldersEntity, when fromEntity() is called, then maps it into a FoldersBackUpModel`() {
        val data = FoldersTestDataProvider.provideDummyTestData()

        val entity = FoldersEntity(id = data.id, name = data.name, type = data.type)

        val model = backupMapper.fromEntity(entity)

        assertEquals(data.id, model.id)
        assertEquals(data.name, model.name)
        assertEquals(data.type, model.type)
    }

    @Test
    fun `given a FoldersBackUpModel, when toEntity() is called, then maps it into a FoldersEntity`() {
        val data = FoldersTestDataProvider.provideDummyTestData()

        val model = FoldersBackUpModel(id = data.id, name = data.name, type = data.type)

        val entity = backupMapper.toEntity(model)

        assertEquals(data.id, entity.id)
        assertEquals(data.name, entity.name)
        assertEquals(data.type, entity.type)
    }
}
