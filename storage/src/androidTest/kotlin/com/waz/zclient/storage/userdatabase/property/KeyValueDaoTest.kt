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
            keyValueDao.insert(KeyValuesEntity(it.key, it.value))
        }
        val storedMessages = keyValueDao.allKeyValues()

        assertEquals(storedMessages.first().key, data.first().key)
        assertEquals(storedMessages.first().value, data.first().value)
        assertEquals(storedMessages.last().key, data.last().key)
        assertEquals(storedMessages.last().value, data.last().value)
        assertEquals(storedMessages.size, numberOfItems)
    }
}
