package com.waz.zclient.storage.userdatabase.folders

import androidx.room.Room
import com.waz.zclient.framework.data.folders.FoldersTestDataProvider
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.folders.FoldersDao
import com.waz.zclient.storage.db.folders.FoldersEntity
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*

class FoldersDaoTest : IntegrationTest() {

    private lateinit var foldersDao: FoldersDao

    private lateinit var userDatabase: UserDatabase

    @Before
    fun setup() {
        userDatabase = Room.inMemoryDatabaseBuilder(
            getApplicationContext(),
            UserDatabase::class.java
        ).build()
        foldersDao = userDatabase.foldersDao()
    }

    @After
    fun tearDown() {
        userDatabase.close()
    }

    @Test
    fun givenAListOfEntries_whenAllFolderIsCalled_thenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        val numberOfItems = 3
        val data = FoldersTestDataProvider.listOfData(numberOfItems)
        data.forEach {
            foldersDao.insert(FoldersEntity(UUID.randomUUID().toString(), it.name, it.type))
        }
        val storedMessages = foldersDao.allFolders()

        assertEquals(storedMessages.first().name, data.first().name)
        assertEquals(storedMessages.first().type, data.first().type)

        assertEquals(storedMessages.last().name, data.last().name)
        assertEquals(storedMessages.last().type, data.last().type)

        assertEquals(storedMessages.size, numberOfItems)
    }
}
