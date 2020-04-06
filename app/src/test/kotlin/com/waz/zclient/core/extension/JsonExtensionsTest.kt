package com.waz.zclient.core.extension

import com.waz.zclient.UnitTest
import org.amshove.kluent.shouldBe
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonExtensionsTest: UnitTest() {

    @Test
    fun `given JSONObject contains a key, when replaceValue is called with that key, then replaces key's value`() {
        val key = "hello"
        val newValue = "world"
        val json = """
            { "$key" : "value" }
        """
        val jsonObject = JSONObject(json)

        jsonObject.replaceValue(key, newValue)

        jsonObject.get(key) shouldBe newValue
    }

    @Test
    fun `given JSONObject does not contains the key, when replaceValue is called with that key, then does not change jsonOnject`() {
        val key = "hello"
        val newValue = "world"
        val json = """
            { "key1" : "value" }
        """
        val jsonObject = JSONObject(json)

        jsonObject.replaceValue(key, newValue)

        assertEquals(jsonObject.toString(), JSONObject(json).toString())
    }
}
