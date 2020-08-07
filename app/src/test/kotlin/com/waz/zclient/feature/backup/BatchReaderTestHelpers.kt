package com.waz.zclient.feature.backup

import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.backup.io.BatchReader
import com.waz.zclient.feature.backup.io.forEach
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.mockito.Mockito.`when`

suspend fun <T> BatchReader<T>.mockNextItems(items: List<T>) {
    val itemsArray = items.map { Either.Right(it) }.plus(Either.Right(null)).toTypedArray()
    `when`(this.readNext()).thenReturn(itemsArray[0], *itemsArray.sliceArray(1 until itemsArray.size))
    `when`(this.hasNext()).thenReturn(itemsArray[0].isRight && itemsArray[0].b != null , *itemsArray.map { it.isRight && it.b != null }.toTypedArray())
}

suspend fun <T> BatchReader<T>.assertItems(expectedItems: List<T>) {
    var count = 0

    this.forEach { next ->
        when {
            count < expectedItems.size -> {
                val expectedItem = expectedItems.getOrNull(count)
                expectedItem?.let { assertEquals(it, next) }
            }
            count == expectedItems.size -> assertEquals(null, next)
            else -> fail("Expected ${expectedItems.size} iterations but got ${count + 1}")
        }
        count++
        Either.Right(Unit)
    }

    assertEquals(expectedItems.size, count)
}
