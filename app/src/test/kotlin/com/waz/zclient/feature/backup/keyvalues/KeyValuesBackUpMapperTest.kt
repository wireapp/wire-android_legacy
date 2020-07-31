package com.waz.zclient.feature.backup.keyvalues

import com.waz.zclient.UnitTest
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
        val entity = KeyValuesEntity(key =  KEY, value = VALUE)

        val model = keyValuesBackUpMapper.fromEntity(entity)

        assertEquals(KEY, model.key)
        assertEquals(VALUE, model.value)
    }

    @Test
    fun `given a KeyValueBackUpModel, when toEntity() is called, then maps it into a KeyValuesEntity`() {
        val model = KeyValuesBackUpModel(key =  KEY, value = VALUE)

        val entity = keyValuesBackUpMapper.toEntity(model)

        assertEquals(KEY, entity.key)
        assertEquals(VALUE, entity.value)
    }

    companion object {
        private const val KEY = "banana"
        private const val VALUE = "orange"
    }
}
