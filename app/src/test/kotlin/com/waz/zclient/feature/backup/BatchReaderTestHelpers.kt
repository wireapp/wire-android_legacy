package com.waz.zclient.feature.backup

import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.backup.io.BatchReader
import com.waz.zclient.feature.backup.io.forEach
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.mockito.Mockito.`when`

suspend fun <T> BatchReader<T>.mockNextItems(items: List<T>) {
    val itemsOrFailure = items.map { Either.Right(it) }
    `when`(this.readNext()).thenReturn(itemsOrFailure[0], *itemsOrFailure.drop(1).toTypedArray())
    if (items.isNotEmpty()) {
        `when`(this.hasNext()).thenReturn(itemsOrFailure[0].isRight, *itemsOrFailure.drop(1).map { it.isRight }.toTypedArray(), false)
    } else {
        `when`(this.hasNext()).thenReturn(false)
    }

}

suspend fun <T> BatchReader<T>.assertItems(expectedItems: List<T>) {
    var count = 0

    this.forEach { next ->
        when {
            count < expectedItems.size -> {
                val expectedItem = expectedItems.getOrNull(count)
                expectedItem?.let { assertEquals(it, next) }
            }
            else -> fail("Expected ${expectedItems.size} iterations but got ${count + 1}")
        }
        count++
        Either.Right(Unit)
    }

    assertEquals(expectedItems.size, count)
}
