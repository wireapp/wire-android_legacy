package com.waz.zclient.storage.userdatabase.receipts

import androidx.room.Room
import com.waz.zclient.framework.data.receipts.ReadReceiptsTestDataProvider
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.receipts.ReadReceiptsDao
import com.waz.zclient.storage.db.receipts.ReadReceiptsEntity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ReadReceiptsDaoTest : IntegrationTest() {

    private lateinit var readReceiptsDao: ReadReceiptsDao

    private lateinit var userDatabase: UserDatabase

    @Before
    fun setup() {
        userDatabase = Room.inMemoryDatabaseBuilder(
            getApplicationContext(),
            UserDatabase::class.java
        ).build()
        readReceiptsDao = userDatabase.readReceiptsDao()
    }

    @After
    fun tearDown() {
        userDatabase.close()
    }

    @Test
    fun givenAListOfEntries_whenAllReadReceiptsIsCalled_thenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        val numberOfItems = 3
        val data = ReadReceiptsTestDataProvider.listOfData(numberOfItems)
        data.forEach {
            readReceiptsDao.insert(
                ReadReceiptsEntity(
                    messageId = it.messageId,
                    userId = it.userId,
                    timestamp = it.timestamp
                )
            )
        }
        val storedReceipts = readReceiptsDao.allReceipts()
        assertEquals(storedReceipts.size, numberOfItems)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs0_thenAssert5ItemIsCollectedAndSizeIs5(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = readReceiptsDao.nextBatch(0, 5)

        assertEquals(storedValues?.size, 5)
        assertEquals(readReceiptsDao.count(), 10)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs5_thenAssert5ItemIsCollectedAndSizeIs10(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = readReceiptsDao.nextBatch(5, 5)
        assertEquals(storedValues?.size, 5)
        assertEquals(readReceiptsDao.count(), 10)
    }

    private suspend fun insertRandomItems(numberOfItems: Int) {
        repeat(numberOfItems) {
            val normalEntity = likesEntity()
            readReceiptsDao.insert(normalEntity)
        }
    }

    private fun likesEntity(id: String = UUID.randomUUID().toString()): ReadReceiptsEntity {
        val data = ReadReceiptsTestDataProvider.provideDummyTestData()
        return ReadReceiptsEntity(
            messageId = data.messageId,
            userId = data.userId,
            timestamp = data.timestamp
        )
    }
}
