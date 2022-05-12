package com.waz.zclient.feature.backup.properties

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.data.property.PropertiesTestDataProvider
import com.waz.zclient.storage.db.property.PropertiesEntity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PropertiesBackUpMapperTest : UnitTest() {

    private lateinit var propertiesBackUpMapper: PropertiesBackUpMapper

    @Before
    fun setUp() {
        propertiesBackUpMapper = PropertiesBackUpMapper()
    }

    @Test
    fun `given a PropertiesEntity, when fromEntity() is called, then maps it into a PropertiesBackUpModel`() {
        val data = PropertiesTestDataProvider.provideDummyTestData()

        val entity = PropertiesEntity(key =  data.key, value = data.value)

        val model = propertiesBackUpMapper.fromEntity(entity)

        assertEquals(data.key, model.key)
        assertEquals(data.value, model.value)
    }

    @Test
    fun `given a PropertiesBackUpModel, when toEntity() is called, then maps it into a PropertiesEntity`() {
        val data = PropertiesTestDataProvider.provideDummyTestData()

        val model = PropertiesBackUpModel(key =  data.key, value = data.value)

        val entity = propertiesBackUpMapper.toEntity(model)

        assertEquals(data.key, entity.key)
        assertEquals(data.value, entity.value)
    }
}
