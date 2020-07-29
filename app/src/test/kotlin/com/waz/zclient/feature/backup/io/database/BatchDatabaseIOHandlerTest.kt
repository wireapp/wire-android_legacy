package com.waz.zclient.feature.backup.io.database

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.eq
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class BatchDatabaseIOHandlerTest : UnitTest() {

    private lateinit var batchReadableDao: BatchReadableDao<Int>

    private lateinit var batchDatabaseIOHandler: BatchDatabaseIOHandler<Int>

    @Test
    fun `given a batchReadableDao, when readIterator() is called, returns an iterator which reads in batches`() {
        val allItems = mutableListOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val batchSize = 3

        batchReadableDao = spy(batchReadableDaoOf(allItems))
        batchDatabaseIOHandler = BatchDatabaseIOHandler(batchReadableDao, batchSize)

        batchDatabaseIOHandler.readIterator().let { iterator ->
            iterator.withIndex().forEach {
                assertEquals(allItems[it.index], it.value)
            }
        }
        verify(batchReadableDao, times(4)).getNextBatch(anyInt(), eq(batchSize))
    }

    @Test
    fun `given a readIterator(), when next() is called, does not request a batch with size more than the remaining count`() {
        val allItems = mutableListOf(1, 2, 3, 4, 5 ,6, 7)
        val batchSize = 3

        batchReadableDao = spy(batchReadableDaoOf(allItems))
        batchDatabaseIOHandler = BatchDatabaseIOHandler(batchReadableDao, batchSize)

        batchDatabaseIOHandler.readIterator().forEach { /* just consume all */}

        //[1, 2, 3], [4, 5, 6]
        verify(batchReadableDao, times(2)).getNextBatch(anyInt(), eq(batchSize))
        //[7]
        verify(batchReadableDao).getNextBatch(anyInt(), eq(1))
    }

    @Test
    fun `given a dao, when write() is called with an iterator, then inserts each next item of the iterator to dao`() {
        batchReadableDao = mock(BatchReadableDao::class.java) as BatchReadableDao<Int>
        val allItems = mutableListOf(1, 2, 3, 4, 5)

        batchDatabaseIOHandler = BatchDatabaseIOHandler(batchReadableDao, 3)

        batchDatabaseIOHandler.write(allItems.listIterator())

        verify(batchReadableDao, times(allItems.size)).insert(any())
        allItems.listIterator().forEach {
            verify(batchReadableDao).insert(it)
        }
    }

    companion object {
        private fun batchReadableDaoOf(list: List<Int>) = object : BatchReadableDao<Int> {
            override fun count(): Int = list.size

            override fun getNextBatch(start: Int, batchSize: Int): List<Int> = list.subList(start, (start + batchSize))

            override fun insert(item: Int) { /*not needed*/ }
        }
    }
}
