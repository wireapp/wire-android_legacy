package com.waz.zclient.feature.backup.io

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either.Left
import com.waz.zclient.core.functional.Either.Right
import com.waz.zclient.feature.backup.mockNextItems
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Ignore
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
                Right(Unit)
            }

            assertEquals(items, nextItems)
            assertEquals(false, batchReader.hasNext())
            assertEquals(Right(Unit), result)
        }
    }

    @Test
    fun `given forEach is called, when all items are read successfully but an action fails, then returns action's failure and does not read any more`() {
        runBlocking {
            val items = listOf("a", "b", "c")
            batchReader.mockNextItems(items)

            val result = batchReader.forEach {
                when (it) {
                    "a" -> Right(Unit)
                    "b" -> Left(DatabaseError)
                    else -> throw AssertionError("Unexpected value $it")
                }
            }

            verify(batchReader, times(2)).readNext()

            assertEquals(Left(DatabaseError), result)
        }
    }

    @Test
    fun `given forEach is called, when readNext() fails, then returns that failure immediately and does not read any more`() {
        runBlocking {
            `when`(batchReader.hasNext()).thenReturn(true, true, true, false)
            `when`(batchReader.readNext()).thenReturn(Right("a"), Left(DatabaseError), Right("b"))

            val result = batchReader.forEach {
                assertEquals("a", it)
                Right(Unit)
            }

            verify(batchReader, times(2)).readNext()

            assertEquals(Left(DatabaseError), result)
        }
    }

    object NumberConversionFailure : FeatureFailure()

    private fun convert(str: String) =
        try {
            Right(str.toInt())
        } catch (ex: NumberFormatException) {
            Left(NumberConversionFailure)
        }

    @Test
    @Ignore
    fun `given empty BatchReader, when mapRight() is called, then return empty list`() {
        runBlocking {
            batchReader.mockNextItems(emptyList<String>())
            val result = batchReader.mapRight { convert(it) }
            assertEquals(Right(emptyList<Int>()), result)
        }
    }

    @Test
    @Ignore
    fun `given BatchReader returning strings which can be converted to numbers, when mapRight() is called, then return a list of numbers`() {
        runBlocking {
            batchReader.mockNextItems(listOf("1", "2", "3"))
            val result = batchReader.mapRight { convert(it) }
            assertEquals(Right(listOf(1, 2, 3)), result)
        }
    }

    @Test
    @Ignore
    fun `given BatchReader returning a string which can't be converted to a number, when mapRight() is called, then return a failure`() {
        runBlocking {
            batchReader.mockNextItems(listOf("a"))
            val result = batchReader.mapRight { convert(it) }
            assertEquals(Left(NumberConversionFailure), result)
        }
    }

    @Test
    @Ignore
    fun `given BatchReader returning strings which can be converted, as well as one which can't, when mapRight() is called, then return a failure`() {
        runBlocking {
            batchReader.mockNextItems(listOf("1", "2", "a", "3"))
            val result = batchReader.mapRight { convert(it) }
            assertEquals(Left(NumberConversionFailure), result)
        }
    }

    @Test
    @Ignore
    fun `given BatchReader returning a strings which can't converted, when mapRight() is called, then make sure strings after the failure are not accessed`() {
        runBlocking {
            batchReader.mockNextItems(listOf("1", "2", "a", "3"))
            var is3Accessed = false
            val result = batchReader.mapRight {
                is3Accessed = it == "3"
                convert(it)
            }
            assertEquals(Left(NumberConversionFailure), result)
            assertFalse(is3Accessed)
        }
    }
}
