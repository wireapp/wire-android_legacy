package com.waz.zclient.core.extension

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either.Left
import com.waz.zclient.core.functional.Either.Right
import org.junit.Assert.assertEquals
import org.junit.Test

class EitherTest : UnitTest() {
    object NumberConversionFailure : FeatureFailure()

    private fun convert(str: String) =
        try {
            Right(str.toInt())
        } catch (ex: NumberFormatException) {
            Left(NumberConversionFailure)
        }

    @Test
    fun `given empty list, when mapRight() is called, then return empty list`() {
        val list = emptyList<String>()
        val result = list.mapRight { convert(it) }
        assertEquals(Right(emptyList<Int>()), result)
    }

    @Test
    fun `given list returning strings which can be converted to numbers, when mapRight() is called, then return a list of numbers`() {
        val list = listOf("1", "2", "3")
        val result = list.mapRight { convert(it) }
        assertEquals(Right(listOf(1, 2, 3)), result)
    }

    @Test
    fun `given list returning a string which can't be converted to a number, when mapRight() is called, then return a failure`() {
        val list = listOf("a")
        val result = list.mapRight { convert(it) }
        assertEquals(Left(NumberConversionFailure), result)
    }

    @Test
    fun `given list returning strings which can be converted, as well as one which can't, when mapRight() is called, then return a failure`() {
        val list = listOf("1", "2", "a", "3")
        val result = list.mapRight { convert(it) }
        assertEquals(Left(NumberConversionFailure), result)
    }

    @Test
    fun `given list returning a strings which can't converted, when mapRight() is called, then make sure strings after the failure are not accessed`() {
        val list = listOf("1", "2", "a", "3")
        var is3Accessed = false
        val result = list.mapRight {
            is3Accessed = it == "3"
            convert(it)
        }
        assertEquals(Left(NumberConversionFailure), result)
        assert(!is3Accessed)
    }
}