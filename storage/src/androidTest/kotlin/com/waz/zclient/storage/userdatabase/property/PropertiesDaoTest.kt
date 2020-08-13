package com.waz.zclient.storage.userdatabase.property

import androidx.room.Room
import com.waz.zclient.framework.data.property.PropertiesTestDataProvider
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.property.PropertiesDao
import com.waz.zclient.storage.db.property.PropertiesEntity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID

class PropertiesDaoTest : IntegrationTest() {

    private lateinit var propertiesDao: PropertiesDao

    private lateinit var userDatabase: UserDatabase

    @Before
    fun setup() {
        userDatabase = Room.inMemoryDatabaseBuilder(
                getApplicationContext(),
                UserDatabase::class.java
        ).build()
        propertiesDao = userDatabase.propertiesDao()
    }

    @After
    fun tearDown() {
        userDatabase.close()
    }

    @Test
    fun givenAListOfEntries_whenAllPropertiesIsCalled_thenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        val numberOfItems = 3
        val data = PropertiesTestDataProvider.listOfData(numberOfItems)
        data.forEach {
            propertiesDao.insert(PropertiesEntity(UUID.randomUUID().toString(), it.value))
        }
        val storedMessages = propertiesDao.allProperties()
        assertEquals(storedMessages.first().value, data.first().value)
        assertEquals(storedMessages.last().value, data.last().value)
        assertEquals(storedMessages.size, numberOfItems)
    }


    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs0_thenAssert5ItemIsCollectedAndSizeIs5(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = propertiesDao.nextBatch(0, 5)

        assertEquals(storedValues?.size, 5)
        assertEquals(propertiesDao.count(), 10)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs5_thenAssert5ItemIsCollectedAndSizeIs10(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = propertiesDao.nextBatch(5, 5)
        assertEquals(storedValues?.size, 5)
        assertEquals(propertiesDao.count(), 10)
    }

    private suspend fun insertRandomItems(numberOfItems: Int) {
        repeat(numberOfItems) {
            val normalEntity = propertiesEntity()
            propertiesDao.insert(normalEntity)
        }
    }

    private fun propertiesEntity(): PropertiesEntity {
        val data = PropertiesTestDataProvider.provideDummyTestData()
        return PropertiesEntity(
            key = data.key,
            value = data.value
        )
    }
}
