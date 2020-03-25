package com.waz.zclient.core.utilities.base64

import com.waz.zclient.UnitTest
import org.junit.Test

class Base64TransformerTest : UnitTest() {

    private val base64Transformer: Base64Transformer = Base64Transformer()

    @Test
    fun `given input as byteArray, then return base 64 string`() {
        val input = "hello".toByteArray()
        val output = base64Transformer.encode(input)

        assert(output == "aGVsbG8=")
    }

//    @Test
//    TODO find a way to resolve java.lang.NoSuchMethodError: java.lang.System.arraycopy
//    fun `given input as string, then return base 64 ByteArray`() {
//        val input = "aGVsbG8="
//        val output = base64Transformer.decode(input)
//
//        assert(output.contentEquals("hello".encodeToByteArray()))
//    }
}
