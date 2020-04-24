package com.waz.zclient.storage.accountdata

import androidx.room.Room
import com.waz.zclient.storage.IntegrationTest
import com.waz.zclient.storage.db.GlobalDatabase
import com.waz.zclient.storage.db.cache.CacheEntryDao
import com.waz.zclient.storage.db.cache.CacheEntryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class CacheEntryDaoTest : IntegrationTest() {

    private lateinit var cacheEntryDao: CacheEntryDao

    private lateinit var globalDatabase: GlobalDatabase

    @Before
    fun setup() {
        globalDatabase = Room.inMemoryDatabaseBuilder(
            getApplicationContext(),
            GlobalDatabase::class.java
        ).build()
        cacheEntryDao = globalDatabase.cacheEntryDao()
    }

    @After
    fun tearDown() {
        globalDatabase.close()
    }

    @Test
    fun givenAListOfEntries_whenCachedEntriesIsCalled_thenAssertDataIsTheSameAsInserted(): Unit = runBlocking {
        val cachedEntries = getListOfCacheEntries()
        cachedEntries.map {
            cacheEntryDao.insertCacheEntry(it)
        }

        val roomActiveAccounts = cacheEntryDao.cacheEntries()
        assert(roomActiveAccounts[0].key == TEST_CACHE_ENTRY_FIRST_ID)
        assert(roomActiveAccounts[1].key == TEST_CACHE_ENTRY_SECOND_ID)
        assert(roomActiveAccounts.size == 2)
        roomActiveAccounts.map {
            assertEquals(it.fileId, TEST_CACHE_ENTRY_FILE_ID)
            assertEquals(it.data, null)
            assertEquals(it.lastUsed, TEST_CACHE_ENTRY_LAST_USED)
            assertEquals(it.timeout, TEST_CACHE_ENTRY_TIME_OUT)
            assertEquals(it.filePath, TEST_CACHE_ENTRY_FILE_PATH)
            assertEquals(it.fileName, TEST_CACHE_ENTRY_FILE_NAME)
            assertEquals(it.mime, TEST_CACHE_ENTRY_MIME)
            assertEquals(it.encKey, TEST_CACHE_ENTRY_ENC_KEY)
            assertEquals(it.length, TEST_CACHE_ENTRY_LENGTH)
        }
        Unit
    }

    private fun createCacheEntry(cacheEntryKey: String) =
        CacheEntryEntity(
            key = cacheEntryKey,
            fileId = TEST_CACHE_ENTRY_FILE_ID,
            data = null,
            lastUsed = TEST_CACHE_ENTRY_LAST_USED,
            timeout = TEST_CACHE_ENTRY_TIME_OUT,
            filePath = TEST_CACHE_ENTRY_FILE_PATH,
            fileName = TEST_CACHE_ENTRY_FILE_NAME,
            mime = TEST_CACHE_ENTRY_MIME,
            encKey = TEST_CACHE_ENTRY_ENC_KEY,
            length = TEST_CACHE_ENTRY_LENGTH
        )

    private fun getListOfCacheEntries() = listOf(
        createCacheEntry(TEST_CACHE_ENTRY_FIRST_ID),
        createCacheEntry(TEST_CACHE_ENTRY_SECOND_ID)
    )

    companion object {
        private const val TEST_CACHE_ENTRY_FIRST_ID = "1"
        private const val TEST_CACHE_ENTRY_SECOND_ID = "2"
        private const val TEST_CACHE_ENTRY_FILE_ID = "fileId"
        private const val TEST_CACHE_ENTRY_LAST_USED = 38847746L
        private const val TEST_CACHE_ENTRY_TIME_OUT = 1582896705028L
        private const val TEST_CACHE_ENTRY_FILE_PATH = "/data/downloads/"
        private const val TEST_CACHE_ENTRY_FILE_NAME = "cachentry2"
        private const val TEST_CACHE_ENTRY_MIME = ".txt"
        private const val TEST_CACHE_ENTRY_ENC_KEY = "AES256"
        private const val TEST_CACHE_ENTRY_LENGTH = 200L
    }
}
