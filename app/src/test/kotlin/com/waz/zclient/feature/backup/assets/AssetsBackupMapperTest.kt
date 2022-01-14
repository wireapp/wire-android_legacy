package com.waz.zclient.feature.backup.assets

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.data.assets.AssetsTestDataProvider
import com.waz.zclient.storage.db.assets.AssetsEntity
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class AssetsBackupMapperTest : UnitTest() {

    private lateinit var assetsBackupMapper: AssetsBackupMapper

    @Before
    fun setUp() {
        assetsBackupMapper = AssetsBackupMapper()
    }

    @Test
    fun `given a AssetsEntity, when fromEntity() is called, then maps it into a AssetsBackUpModel`() {
        val data = AssetsTestDataProvider.provideDummyTestData()

        val entity = AssetsEntity(
            id = data.id,
            token = data.token,
            domain = data.domain,
            name = data.name,
            encryption = data.encryption,
            mime = data.mime,
            sha = data.sha,
            size = data.size,
            source = data.source,
            preview = data.preview,
            details = data.details
        )

        val model = assetsBackupMapper.fromEntity(entity)

        assertEquals(data.id, model.id)
        assertEquals(data.token, model.token)
        assertEquals(data.domain, model.domain)
        assertEquals(data.name, model.name)
        assertEquals(data.encryption, model.encryption)
        assertEquals(data.mime, model.mime)
        assertEquals(data.sha, model.sha)
        assertEquals(data.size, model.size)
        assertEquals(data.source, model.source)
        assertEquals(data.preview, model.preview)
        assertEquals(data.details, model.details)
    }

    @Test
    fun `given a AssetsBackUpModel, when toEntity() is called, then maps it into a AssetsEntity`() {
        val data = AssetsTestDataProvider.provideDummyTestData()

        val model = AssetsBackUpModel(
            id = data.id,
            token = data.token,
            domain = data.domain,
            name = data.name,
            encryption = data.encryption,
            mime = data.mime,
            sha = data.sha,
            size = data.size,
            source = data.source,
            preview = data.preview,
            details = data.details
        )

        val entity = assetsBackupMapper.toEntity(model)

        assertEquals(data.id, entity.id)
        assertEquals(data.token, entity.token)
        assertEquals(data.domain, entity.domain)
        assertEquals(data.name, entity.name)
        assertEquals(data.encryption, entity.encryption)
        assertEquals(data.mime, entity.mime)
        assertEquals(data.sha, entity.sha)
        assertEquals(data.size, entity.size)
        assertEquals(data.source, entity.source)
        assertEquals(data.preview, entity.preview)
        assertEquals(data.details, entity.details)
    }
}
