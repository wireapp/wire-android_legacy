package com.waz.zclient.storage.userdatabase.messages

import androidx.room.Room
import com.waz.zclient.framework.data.messages.LikesTestDataProvider
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.messages.LikesDao
import com.waz.zclient.storage.db.messages.LikesEntity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID

class LikesDaoTest : IntegrationTest() {

    private lateinit var likesDao: LikesDao

    private lateinit var userDatabase: UserDatabase

    @Before
    fun setup() {
        userDatabase = Room.inMemoryDatabaseBuilder(
                getApplicationContext(),
                UserDatabase::class.java
        ).build()
        likesDao = userDatabase.likesDao()
    }

    @After
    fun tearDown() {
        userDatabase.close()
    }

    @Test
    fun givenAListOfEntries_whenAllLikesIsCalled_thenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        val numberOfItems = 3
        val data = LikesTestDataProvider.listOfData(numberOfItems)
        data.forEach {
            likesDao.insert(
                LikesEntity(
                    messageId = it.messageId,
                    userId = it.userId,
                    timeStamp = it.timeStamp,
                    action = it.action
                )
            )
        }
        val storedLikes = likesDao.allLikes()
        assertEquals(storedLikes.size, numberOfItems)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs0_thenAssert5ItemIsCollectedAndSizeIs5(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = likesDao.nextBatch(0, 5)

        assertEquals(storedValues?.size, 5)
        assertEquals(likesDao.count(), 10)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs5_thenAssert5ItemIsCollectedAndSizeIs10(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = likesDao.nextBatch(5, 5)
        assertEquals(storedValues?.size, 5)
        assertEquals(likesDao.count(), 10)
    }

    private suspend fun insertRandomItems(numberOfItems: Int) {
        repeat(numberOfItems) {
            val normalEntity = likesEntity()
            likesDao.insert(normalEntity)
        }
    }

    private fun likesEntity(id: String = UUID.randomUUID().toString()): LikesEntity {
        val data = LikesTestDataProvider.provideDummyTestData()
        return LikesEntity(
            messageId = data.messageId,
            userId = data.userId,
            timeStamp = data.timeStamp,
            action = data.action
        )
    }
}
