package com.waz.zclient.storage.userdatabase.assets

import androidx.room.Room
import com.waz.zclient.framework.data.assets.AssetsTestDataProvider
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.assets.AssetsDao
import com.waz.zclient.storage.db.assets.AssetsEntity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*

class AssetsDaoTest : IntegrationTest() {

    private lateinit var assetsDao: AssetsDao

    private lateinit var userDatabase: UserDatabase

    @Before
    fun setup() {
        userDatabase = Room.inMemoryDatabaseBuilder(
            getApplicationContext(),
            UserDatabase::class.java
        ).build()
        assetsDao = userDatabase.assetsDao()
    }

    @After
    fun tearDown() {
        userDatabase.close()
    }

    @Test
    fun givenAListOfEntries_whenAllKeyValuesIsCalled_thenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        val numberOfItems = 3
        val data = AssetsTestDataProvider.listOfData(numberOfItems)
        repeat(data.size) {
            assetsDao.insert(assetsEntity())
        }
        val storedMessages = assetsDao.allAssets()
        assertEquals(storedMessages.size, numberOfItems)
    }


    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs0_thenAssert5ItemIsCollectedAndSizeIs5(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = assetsDao.nextBatch(0, 5)

        assertEquals(storedValues?.size, 5)
        assertEquals(assetsDao.count(), 10)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs5_thenAssert5ItemIsCollectedAndSizeIs10(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = assetsDao.nextBatch(5, 5)
        assertEquals(storedValues?.size, 5)
        assertEquals(assetsDao.count(), 10)
    }

    private suspend fun insertRandomItems(numberOfItems: Int) {
        repeat(numberOfItems) {
            val normalEntity = assetsEntity()
            assetsDao.insert(normalEntity)
        }
    }

    private fun assetsEntity(id: String = UUID.randomUUID().toString()): AssetsEntity {
        val data = AssetsTestDataProvider.provideDummyTestData()
        return AssetsEntity(
            id = id,
            domain = null,
            token = data.token,
            name = data.name,
            encryption = data.encryption,
            mime = data.mime,
            sha = data.sha,
            size = data.size,
            source = data.source,
            preview = data.preview,
            details = data.details
        )
    }
}
