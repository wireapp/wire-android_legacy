package com.waz.zclient.feature.backup.io.file

import com.waz.zclient.UnitTest
import com.waz.zclient.core.utilities.converters.JsonConverter
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class JsonConverterTest : UnitTest() {

    @Serializable
    private data class TestPerson(
        val name: String,
        val age: Int,
        val children: List<String>
    )

    private lateinit var jsonConverter: JsonConverter<TestPerson>

    @Before
    fun setUp() {
        jsonConverter = JsonConverter(TestPerson.serializer())
    }

    @Test
    fun `given an object, when toJson is called, creates a json string`() {
        val jsonStr = jsonConverter.toJson(TEST_PERSON)

        assertEquals(TEST_PERSON_STRING, jsonStr)
    }

    @Test
    fun `given a json string, when fromJson is called, parses the model`() {
        val model = jsonConverter.fromJson(TEST_PERSON_STRING)

        assertEquals(TEST_PERSON, model)
    }

    companion object {
        private const val TEST_NAME = "John"
        private const val CHILD1 = "Amy"
        private const val CHILD2 = "Bob"
        private val TEST_CHILDREN = listOf(CHILD1, CHILD2)
        private const val TEST_AGE = 35

        private val TEST_PERSON_STRING = """
            {"name":"$TEST_NAME","age":$TEST_AGE,"children":["$CHILD1","$CHILD2"]}
        """.trimIndent()

        private val TEST_PERSON = TestPerson(name = TEST_NAME, age = TEST_AGE, children = TEST_CHILDREN)
    }
}
