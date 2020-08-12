package com.waz.zclient.storage.userdatabase.buttons


import androidx.room.Room
import com.waz.zclient.framework.data.buttons.ButtonsTestDataProvider
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.buttons.ButtonsDao
import com.waz.zclient.storage.db.buttons.ButtonsEntity
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*

class ButtonsDaoTest : IntegrationTest() {

    private lateinit var buttonsDao: ButtonsDao

    private lateinit var userDatabase: UserDatabase

    @Before
    fun setup() {
        userDatabase = Room.inMemoryDatabaseBuilder(
                getApplicationContext(),
                UserDatabase::class.java
        ).build()
        buttonsDao = userDatabase.buttonsDao()
    }

    @After
    fun tearDown() {
        userDatabase.close()
    }

    @Test
    fun givenAListOfEntries_whenAllButtonsIsCalled_thenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        val numberOfItems = 3
        val data = ButtonsTestDataProvider.listOfData(numberOfItems)
        data.forEach {
            buttonsDao.insert(
                ButtonsEntity(
                    messageId = it.messageId,
                    buttonId = it.buttonId,
                    title = it.title,
                    ordinal = it.ordinal,
                    state = it.state
                )
            )
        }
        val storedButtons = buttonsDao.allButtons()
        TestCase.assertEquals(storedButtons.size, numberOfItems)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs0_thenAssert5ItemIsCollectedAndSizeIs5(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = buttonsDao.nextBatch(0, 5)

        TestCase.assertEquals(storedValues?.size, 5)
        TestCase.assertEquals(buttonsDao.count(), 10)
    }

    @Test
    fun givenAListOfEntries_whenGetBatchIsCalledAndOffsetIs5_thenAssert5ItemIsCollectedAndSizeIs10(): Unit = runBlocking {
        insertRandomItems(10)

        val storedValues = buttonsDao.nextBatch(5, 5)
        TestCase.assertEquals(storedValues?.size, 5)
        TestCase.assertEquals(buttonsDao.count(), 10)
    }

    private suspend fun insertRandomItems(numberOfItems: Int) {
        repeat(numberOfItems) {
            val normalEntity = buttonsEntity()
            buttonsDao.insert(normalEntity)
        }
    }

    private fun buttonsEntity(id: String = UUID.randomUUID().toString()): ButtonsEntity {
        val data = ButtonsTestDataProvider.provideDummyTestData()
        return ButtonsEntity(
            messageId = data.messageId,
            buttonId = data.buttonId,
            title = data.title,
            ordinal = data.ordinal,
            state = data.state
        )
    }
}
