package com.waz.zclient.feature.backup.keyvalues

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import com.waz.zclient.storage.db.property.KeyValuesDao
import com.waz.zclient.storage.db.property.KeyValuesEntity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class KeyValuesBackUpDaoTest : UnitTest() {

    private lateinit var keyValuesBackUpDao: KeyValuesBackUpDao

    @Mock
    private lateinit var keyValuesDao: KeyValuesDao

    @Before
    fun setup() {
        keyValuesBackUpDao = KeyValuesBackUpDao(keyValuesDao)
    }

    @Test
    fun `given all items are requested, then get items from dao`() {
        runBlocking {
            keyValuesBackUpDao.allItems()

            verify(keyValuesDao).allKeyValues()
        }
    }

    @Test
    fun `given entity, when insert is called, then insert same entity`() {
        runBlocking {
            val entity = mock(KeyValuesEntity::class.java)

            keyValuesBackUpDao.insert(entity)

            verify(keyValuesDao).insert(entity)
        }
    }
}
