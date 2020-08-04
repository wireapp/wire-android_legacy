package com.waz.zclient.feature.backup.io

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.backup.assertItems
import com.waz.zclient.feature.backup.mockNextItems
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class BatchReaderExtensionsTest : UnitTest() {

    @Mock
    private lateinit var batchReader: BatchReader<String>

    @Test
    fun `given forEach is called, when all items are read successfully and actions are successful, then returns Right(Unit)`() {
        runBlocking {
            val items = listOf("a", "b", "c")
            batchReader.mockNextItems(items)

            val nextItems = mutableListOf<String>()

            val result = batchReader.forEach {
                nextItems.add(it)
                Either.Right(Unit)
            }

            assertEquals(items, nextItems)
            assertEquals(Either.Right(null), batchReader.readNext())
            assertEquals(Either.Right(Unit), result)
        }
    }

    @Test
    fun `given forEach is called, when all items are read successfully but an action fails, then returns action's failure and does not read any more`() {
        runBlocking {
            val items = listOf("a", "b", "c")
            batchReader.mockNextItems(items)

            val result = batchReader.forEach {
                when (it) {
                    "a" -> Either.Right(Unit)
                    "b" -> Either.Left(DatabaseError)
                    else -> throw AssertionError("Unexpected value $it")
                }
            }

            verify(batchReader, times(2)).readNext()

            assertEquals(Either.Left(DatabaseError), result)
        }
    }

    @Test
    fun `given forEach is called, when readNext() fails, then returns that failure immediately and does not read any more`() {
        runBlocking {
            `when`(batchReader.hasNext()).thenReturn(true, true, true, false)
            `when`(batchReader.readNext()).thenReturn(Either.Right("a"), Either.Left(DatabaseError), Either.Right("b"))

            val result = batchReader.forEach {
                assertEquals("a", it)
                Either.Right(Unit)
            }

            verify(batchReader, times(2)).readNext()

            assertEquals(Either.Left(DatabaseError), result)
        }
    }
}
