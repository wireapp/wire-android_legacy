package com.waz.zclient.feature.backup.io.file

import com.waz.zclient.UnitTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.io.File

class BackUpFileIOHandlerTest : UnitTest() {

    @Mock
    private lateinit var jsonConverter: JsonConverter<Int>

    private lateinit var backUpFileIOHandler: BackUpFileIOHandler<Int>

    @Test
    fun `given an iterator, when write() is called, then creates a new file and writes contents to it`() =
        runTestWithFile(uniqueFileName()) {
            testFileWrite(it)
        }

    @Test
    fun `given an existing file, when write() is called, then erases the file's contents and writes on top of it`() =
        runTestWithFile(uniqueFileName()) {

            File(it).writeText("Some dummy \ntext 123..%x&")

            testFileWrite(it)
        }

    private fun testFileWrite(uniqueFileName: String) {
        val items = listOf(1, 2, 3)
        `when`(jsonConverter.toJson(1)).thenReturn("line 1")
        `when`(jsonConverter.toJson(2)).thenReturn("line 2")
        `when`(jsonConverter.toJson(3)).thenReturn("line 3")

        backUpFileIOHandler = BackUpFileIOHandler(uniqueFileName, jsonConverter)

        backUpFileIOHandler.write(items.listIterator())

        with(File(uniqueFileName)) {
            useLines { seq ->
                seq.iterator().withIndex().forEach {
                    assertEquals("line ${it.index + 1}", it.value)
                }
            }
        }
    }

    @Test
    fun `given an existing file, when readIterator() is called, then returns an iterator which reads it line by line`() =
        runTestWithFile(uniqueFileName()) { fileName ->

            File(fileName).also {
                it.delete()
                it.createNewFile()
                it.writeText("line 1\nline 2\nline 3\n")
            }

            `when`(jsonConverter.fromJson("line 1")).thenReturn(1)
            `when`(jsonConverter.fromJson("line 2")).thenReturn(2)
            `when`(jsonConverter.fromJson("line 3")).thenReturn(3)

            backUpFileIOHandler = BackUpFileIOHandler(fileName, jsonConverter)

            backUpFileIOHandler.readIterator().withIndex().forEach {
                assertEquals(it.index + 1, it.value)
            }
        }

    private fun runTestWithFile(fileName: String, testWithFile: (String) -> Unit): Unit {
        try {
            testWithFile(fileName)
        } catch (ex: Exception) {
            fail("Exception occured: $ex")
        } finally {
            File(fileName).deleteOnExit()
        }
    }

    companion object {
        private fun uniqueFileName() = "backUpFileIOHandlerTest_${System.currentTimeMillis()}.txt"
    }
}
