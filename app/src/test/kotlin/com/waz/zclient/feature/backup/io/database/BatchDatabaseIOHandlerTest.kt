package com.waz.zclient.feature.backup.io.database

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.functional.Either
import com.waz.zclient.eq
import com.waz.zclient.feature.backup.assertItems
import com.waz.zclient.feature.backup.io.BatchReader
import com.waz.zclient.feature.backup.io.forEach
import com.waz.zclient.feature.backup.mockNextItems
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.*

class BatchDatabaseIOHandlerTest : UnitTest() {

    private lateinit var batchReadableDao: BatchReadableDao<Int>

    @Mock
    private lateinit var batchReader: BatchReader<Int>

    private lateinit var batchDatabaseIOHandler: BatchDatabaseIOHandler<Int>

    @Test
    fun `given a batchReadableDao, when readIterator() is called, returns an iterator which reads in batches`() {
        val allItems = mutableListOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val batchSize = 3

        batchReadableDao = spy(batchReadableDaoOf(allItems))
        batchDatabaseIOHandler = BatchDatabaseIOHandler(batchReadableDao, batchSize)

        runBlocking {
            batchDatabaseIOHandler.readIterator().assertItems(allItems)
            verify(batchReadableDao, times(4)).nextBatch(anyInt(), eq(batchSize))
        }
    }

    @Test
    fun `given a readIterator(), when next() is called, does not request a batch with size more than the remaining count`() {
        runBlocking {
            val allItems = mutableListOf(1, 2, 3, 4, 5, 6, 7)
            val batchSize = 3

            batchReadableDao = spy(batchReadableDaoOf(allItems))
            batchDatabaseIOHandler = BatchDatabaseIOHandler(batchReadableDao, batchSize)

            batchDatabaseIOHandler.readIterator().forEach { Either.Right(Unit)/* just consume all */ }

            //[1, 2, 3], [4, 5, 6]
            verify(batchReadableDao, times(2)).nextBatch(anyInt(), eq(batchSize))
            //[7]
            verify(batchReadableDao).nextBatch(anyInt(), eq(1))
        }
    }

    @Test
    fun `given a dao, when write() is called with an iterator, then inserts each next item of the iterator to dao`() {
        runBlocking {
            batchReadableDao = mock(BatchReadableDao::class.java) as BatchReadableDao<Int>
            val allItems = mutableListOf(1, 2, 3, 4, 5)
            batchReader.mockNextItems(allItems)

            batchDatabaseIOHandler = BatchDatabaseIOHandler(batchReadableDao, 3)

            batchDatabaseIOHandler.write(batchReader)

            verify(batchReadableDao, times(allItems.size)).insert(any())
            allItems.listIterator().forEach {
                verify(batchReadableDao).insert(it)
            }
        }
    }

    companion object {
        private fun batchReadableDaoOf(list: List<Int>) = object : BatchReadableDao<Int> {
            override suspend fun count(): Int = list.size

            override suspend fun nextBatch(start: Int, batchSize: Int): List<Int> = list.subList(start, (start + batchSize))

            override suspend fun insert(item: Int) { /*not needed*/ }
        }
    }
}
