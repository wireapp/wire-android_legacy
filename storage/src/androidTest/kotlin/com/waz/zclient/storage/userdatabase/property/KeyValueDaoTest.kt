package com.waz.zclient.storage.userdatabase.property

import androidx.room.Room
import com.waz.zclient.framework.data.property.KeyValueTestDataProvider
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.property.KeyValuesDao
import com.waz.zclient.storage.db.property.KeyValuesEntity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*

class KeyValueDaoTest : IntegrationTest() {

    private lateinit var keyValueDao: KeyValuesDao

    private lateinit var userDatabase: UserDatabase

    @Before
    fun setup() {
        userDatabase = Room.inMemoryDatabaseBuilder(
            getApplicationContext(),
            UserDatabase::class.java
        ).build()
        keyValueDao = userDatabase.keyValuesDao()
    }

    @After
    fun tearDown() {
        userDatabase.close()
    }

    @Test
    fun givenAListOfEntries_whenAllKeyValuesIsCalled_thenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        val numberOfItems = 3
        val data = KeyValueTestDataProvider.listOfData(numberOfItems)
        data.forEach {
            keyValueDao.insert(KeyValuesEntity(UUID.randomUUID().toString(), it.value))
        }
        val storedMessages = keyValueDao.allKeyValues()
        assertEquals(storedMessages.first().value, data.first().value)
        assertEquals(storedMessages.last().value, data.last().value)
        assertEquals(storedMessages.size, numberOfItems)
    }


    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs0_thenAssert5ItemIsCollectedAndSizeIs5(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = keyValueDao.nextBatch(0, 5)

        assertEquals(storedValues?.size, 5)
        assertEquals(keyValueDao.count(), 10)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs5_thenAssert5ItemIsCollectedAndSizeIs10(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = keyValueDao.nextBatch(5, 5)
        assertEquals(storedValues?.size, 5)
        assertEquals(keyValueDao.count(), 10)
    }

    private suspend fun insertRandomItems(numberOfItems: Int) {
        repeat(numberOfItems) {
            val normalEntity = keyValueEntity()
            keyValueDao.insert(normalEntity)
        }
    }

    private fun keyValueEntity(): KeyValuesEntity {
        val data = KeyValueTestDataProvider.provideDummyTestData()
        return KeyValuesEntity(
            key = data.key,
            value = data.value
        )
    }
}
