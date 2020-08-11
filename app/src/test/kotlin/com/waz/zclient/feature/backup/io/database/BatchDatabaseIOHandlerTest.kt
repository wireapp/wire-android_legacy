package com.waz.zclient.feature.backup.io.database

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.functional.Either
import com.waz.zclient.eq
import com.waz.zclient.feature.backup.assertItems
import com.waz.zclient.feature.backup.io.BatchReader
import com.waz.zclient.feature.backup.io.forEach
import com.waz.zclient.feature.backup.mockNextItems
import com.waz.zclient.storage.db.BatchDao
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class BatchDatabaseIOHandlerTest : UnitTest() {

    private lateinit var batchDao: BatchDao<Int>

    @Mock
    private lateinit var batchReader: BatchReader<List<Int>>

    private lateinit var batchDatabaseIOHandler: BatchDatabaseIOHandler<Int>

    @Test
    fun `given a batchReadableDao, when readIterator() is called, returns an iterator which reads in batches`() {
        val allItems = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val itemsInBatches = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(10, 11, 12))
        val batchSize = 3

        batchDao = spy(batchReadableDaoOf(allItems))
        batchDatabaseIOHandler = BatchDatabaseIOHandler(batchDao, batchSize)

        runBlocking {
            batchDatabaseIOHandler.readIterator().assertItems(itemsInBatches)
            verify(batchDao, times(4)).nextBatch(ArgumentMatchers.anyInt(), eq(batchSize))
        }
    }

    @Test
    fun `given a readIterator(), when next() is called, does not request a batch with size more than the remaining count`() {
        runBlocking {
            val allItems = listOf(1, 2, 3, 4, 5, 6, 7)
            val batchSize = 3

            batchDao = spy(batchReadableDaoOf(allItems))
            batchDatabaseIOHandler = BatchDatabaseIOHandler(batchDao, batchSize)

            batchDatabaseIOHandler.readIterator().forEach { Either.Right(Unit)/* just consume all */ }

            //[1, 2, 3], [4, 5, 6]
            verify(batchDao, times(2)).nextBatch(ArgumentMatchers.anyInt(), eq(batchSize))
            //[7]
            verify(batchDao).nextBatch(ArgumentMatchers.anyInt(), eq(1))
        }
    }

    @Test
    fun `given a dao, when write() is called with an iterator, then inserts each next item of the iterator to dao`() {
        runBlocking {
            batchDao = mock(BatchDao::class.java) as BatchDao<Int>
            val allItems = listOf(listOf(1, 2, 3), listOf(4, 5))
            batchReader.mockNextItems(allItems)

            batchDatabaseIOHandler = BatchDatabaseIOHandler(batchDao, 3)

            batchDatabaseIOHandler.write(batchReader)

            verify(batchDao, times(allItems.size)).insertAll(any())
            allItems.listIterator().forEach {
                verify(batchDao).insertAll(it)
            }
        }
    }

    companion object {
        private fun batchReadableDaoOf(list: List<Int>) = object : BatchDao<Int> {
            override suspend fun count(): Int = list.size

            override suspend fun nextBatch(start: Int, batchSize: Int): List<Int>? =
                if (start < list.size) list.subList(start, (start + batchSize)) else null

            override suspend fun insert(item: Int) { /*not needed*/ }

            override suspend fun insertAll(items: List<Int>) {/*not needed*/ }
        }
    }
}
