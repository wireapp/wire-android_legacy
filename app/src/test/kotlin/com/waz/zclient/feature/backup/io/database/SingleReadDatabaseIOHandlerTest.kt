package com.waz.zclient.feature.backup.io.database

import com.waz.zclient.UnitTest
import com.waz.zclient.feature.backup.assertItems
import com.waz.zclient.feature.backup.io.BatchReader
import com.waz.zclient.feature.backup.mockNextItems
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class SingleReadDatabaseIOHandlerTest : UnitTest() {

    @Mock
    private lateinit var singleReadDao: SingleReadDao<Int>

    @Mock
    private lateinit var batchReader: BatchReader<Int>

    private lateinit var singleReadDatabaseIOHandler: SingleReadDatabaseIOHandler<Int>

    @Before
    fun setUp() {
        singleReadDatabaseIOHandler = SingleReadDatabaseIOHandler(singleReadDao)
    }

    @Test
    fun `given an iterator, when write() is called, then inserts every item received into dao`() {
        runBlocking {
            val items = listOf(1, 2, 3)
            batchReader.mockNextItems(items)

            singleReadDatabaseIOHandler.write(batchReader)

            verify(singleReadDao, times(items.size)).insert(anyInt())
            items.forEach {
                verify(singleReadDao).insert(it)
            }
        }
    }

    @Test
    fun `given a singleReadDao, when readIterator() is called, then fetches all items at once and returns the proper iterator`() {
        runBlocking {
            val allItems = listOf(1, 2, 3, 4)
            `when`(singleReadDao.allItems()).thenReturn(allItems)

            val readIterator = singleReadDatabaseIOHandler.readIterator()

            readIterator.assertItems(allItems)
            verify(singleReadDao).allItems()
        }
    }
}
