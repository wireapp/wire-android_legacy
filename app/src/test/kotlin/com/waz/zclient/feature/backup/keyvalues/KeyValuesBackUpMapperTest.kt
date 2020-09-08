package com.waz.zclient.feature.backup.keyvalues

import com.waz.zclient.UnitTest
import com.waz.zclient.framework.data.property.KeyValueTestDataProvider
import com.waz.zclient.storage.db.property.KeyValuesEntity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KeyValuesBackUpMapperTest : UnitTest() {

    private lateinit var keyValuesBackUpMapper: KeyValuesBackUpMapper

    @Before
    fun setUp() {
        keyValuesBackUpMapper = KeyValuesBackUpMapper()
    }

    @Test
    fun `given a KeyValuesEntity, when fromEntity() is called, then maps it into a KeyValueBackUpModel`() {
        val data = KeyValueTestDataProvider.provideDummyTestData()

        val entity = KeyValuesEntity(key =  data.key, value = data.value)

        val model = keyValuesBackUpMapper.fromEntity(entity)

        assertEquals(data.key, model.key)
        assertEquals(data.value, model.value)
    }

    @Test
    fun `given a KeyValueBackUpModel, when toEntity() is called, then maps it into a KeyValuesEntity`() {
        val data = KeyValueTestDataProvider.provideDummyTestData()

        val model = KeyValuesBackUpModel(key =  data.key, value = data.value)

        val entity = keyValuesBackUpMapper.toEntity(model)

        assertEquals(data.key, entity.key)
        assertEquals(data.value, entity.value)
    }
}
