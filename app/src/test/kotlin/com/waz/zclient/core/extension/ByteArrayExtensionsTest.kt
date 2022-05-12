package com.waz.zclient.core.extension

import com.waz.zclient.UnitTest
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldEqual
import org.junit.Test

class ByteArrayExtensionsTest : UnitTest() {

    @Test
    fun `given a byte array of size below 2x split size, when describe() is called, the result should be the same as in contentToString + the size`() {
        val byteArray = byteArrayOf(10, 20, 30, 40, 50, 60, 70)
        val res = byteArray.describe(splitAt = 4)
        res shouldContain byteArray.contentToString()
        res shouldContain "(${byteArray.size})"
    }

    @Test
    fun `given an empty byte array, when describe() is called, the result should be the empty array + zero as the size`() {
        val byteArray = byteArrayOf()
        byteArray.describe() shouldEqual "[] (0)"
    }

    @Test
    fun `given a byte array of size above 2x split size, when describe() is called, the result should show first and last bytes + the size`() {
        val byteArray = byteArrayOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
        val res = byteArray.describe(splitAt = 4)
        res shouldContain byteArray.take(4).joinToString(", ")
        res shouldContain "..."
        res shouldContain byteArray.drop(byteArray.size - 4).joinToString(", ")
        res shouldContain "(${byteArray.size})"
    }
}