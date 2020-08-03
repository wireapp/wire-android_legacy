package com.waz.zclient.feature.backup.io.database

import com.waz.zclient.UnitTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private lateinit var singleReadDatabaseIOHandler: SingleReadDatabaseIOHandler<Int>

    @Before
    fun setUp() {
        singleReadDatabaseIOHandler = SingleReadDatabaseIOHandler(singleReadDao)
    }

    @Test
    fun `given an iterator, when write() is called, then inserts every item received into dao`() {
        val items = listOf(1, 2, 3)

        singleReadDatabaseIOHandler.write(items.iterator())

        verify(singleReadDao, times(items.size)).insert(anyInt())
        items.forEach {
            verify(singleReadDao).insert(it)
        }
    }

    @Test
    fun `given a singleReadDao, when readIterator() is called, then fetches all items at once and returns the proper iterator`() {
        val allItems = listOf(1, 2, 3, 4)
        `when`(singleReadDao.getAll()).thenReturn(allItems)

        val readIterator = singleReadDatabaseIOHandler.readIterator()

        readIterator.withIndex().forEach {
            assertEquals(allItems[it.index], it.value)
        }
        assertFalse(readIterator.hasNext())

        verify(singleReadDao).getAll()
    }
}
