package com.waz.zclient.feature.backup.io.file

import com.waz.zclient.UnitTest
import com.waz.zclient.core.utilities.converters.JsonConverter
import com.waz.zclient.feature.backup.assertItems
import com.waz.zclient.feature.backup.io.BatchReader
import com.waz.zclient.feature.backup.mockNextItems
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.io.File

class BackUpFileIOHandlerTest : UnitTest() {

    @Mock
    private lateinit var jsonConverter: JsonConverter<Int>

    @Mock
    private lateinit var batchReader: BatchReader<Int>

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

    private fun testFileWrite(uniqueFileName: String) = runBlocking {
        batchReader.mockNextItems(listOf(1, 2, 3))
        `when`(jsonConverter.toJson(1)).thenReturn("line 1")
        `when`(jsonConverter.toJson(2)).thenReturn("line 2")
        `when`(jsonConverter.toJson(3)).thenReturn("line 3")

        backUpFileIOHandler = BackUpFileIOHandler(uniqueFileName, jsonConverter)

        backUpFileIOHandler.write(batchReader)

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
                it.writeText("line 1\n line 2\n line 3\n")
            }

            backUpFileIOHandler = BackUpFileIOHandler(fileName, jsonConverter)

            runBlocking { backUpFileIOHandler.readIterator().assertItems(listOf(1, 2, 3)) }
        }

    private fun runTestWithFile(fileName: String, testWithFile: (String) -> Unit): Unit {
        try {
            testWithFile(fileName)
        } catch (ex: Exception) {
            fail("Exception occurred: $ex")
        } finally {
            File(fileName).deleteOnExit()
        }
    }

    companion object {
        private fun uniqueFileName() = "backUpFileIOHandlerTest_${System.currentTimeMillis()}.txt"
    }
}
