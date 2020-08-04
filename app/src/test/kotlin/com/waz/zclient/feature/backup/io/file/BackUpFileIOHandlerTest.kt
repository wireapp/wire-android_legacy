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
    private lateinit var batchReader: BatchReader<List<Int>>

    private lateinit var backUpFileIOHandler: BackUpFileIOHandler<Int>

    @Test
    fun `given an iterator, when write() is called, then create a new file and writes contents to it`() =
        runTestWithFile(uniqueFileName()) { name ->
            runBlocking {
                val jsonStr = """
                    [
                        "line 1",
                        "line 2",
                        "line 3"
                    ]
                """.trimIndent()

                batchReader.mockNextItems(listOf(listOf(1, 2, 3)))
                `when`(jsonConverter.toJsonList(listOf(1, 2, 3))).thenReturn(jsonStr)

                val tempDir = createTempDir()
                backUpFileIOHandler = BackUpFileIOHandler(name, jsonConverter, tempDir)
                backUpFileIOHandler.write(batchReader)
                val createdFiles = getFilesWithPrefix(tempDir, name)
                assertEquals(1, createdFiles.size)
                with(createdFiles[0]) {
                    val text = this.readText()
                    assertEquals(jsonStr, text)
                }
            }
        }

    @Test
    fun `given an iterator, when write() is called twice, then replace contents of the file`() =
        runTestWithFile(uniqueFileName()) { name ->
            runBlocking {
                val jsonStr1 = """
                    [
                        "line 1",
                        "line 2",
                        "line 3"
                    ]
                """.trimIndent()
                val jsonStr2 = """
                    [
                        "line 4",
                        "line 5",
                        "line 6"
                    ]
                """.trimIndent()

                val tempDir = createTempDir()

                `when`(jsonConverter.toJsonList(listOf(1, 2, 3))).thenReturn(jsonStr1)
                `when`(jsonConverter.toJsonList(listOf(4, 5, 6))).thenReturn(jsonStr2)

                backUpFileIOHandler = BackUpFileIOHandler(name, jsonConverter, tempDir)

                batchReader.mockNextItems(listOf(listOf(1, 2, 3)))
                backUpFileIOHandler.write(batchReader)
                val createdFiles1 = getFilesWithPrefix(tempDir, name)
                assertEquals(1, createdFiles1.size)
                val fileName = createdFiles1[0].name

                batchReader.mockNextItems(listOf(listOf(4, 5, 6)))

                backUpFileIOHandler.write(batchReader)
                val createdFiles2 = getFilesWithPrefix(tempDir, name)
                assertEquals(1, createdFiles2.size)
                assertEquals(fileName, createdFiles2[0].name)

                with(createdFiles2[0]) {
                    val text = this.readText()
                    assertEquals(jsonStr2, text)
                }
            }
        }

    @Test
    fun `given an existing file, when readIterator() is called, then returns an iterator which reads it line by line`() =
        runTestWithFile(uniqueFileName()) { fileName ->
            val tempDir = createTempDir()

            val jsonStr = """
                [
                    "line 1",
                    "line 2",
                    "line 3"
                ]
            """.trimIndent()

            val numbers = listOf(1, 2, 3)

            `when`(jsonConverter.toJsonList(numbers)).thenReturn(jsonStr)
            `when`(jsonConverter.fromJsonList(jsonStr)).thenReturn(numbers)

            backUpFileIOHandler = BackUpFileIOHandler(fileName, jsonConverter, tempDir)

            runBlocking {
                batchReader.mockNextItems(listOf(numbers))
                backUpFileIOHandler.write(batchReader)
                backUpFileIOHandler.readIterator().assertItems(listOf(numbers))
            }
        }

    companion object {
        private fun uniqueFileName() = "backUpFileIOHandlerTest_${System.currentTimeMillis()}.txt"

        private fun getFilesWithPrefix(dir: File, prefix: String) = dir.walkTopDown().toList().filter { it.name.startsWith(prefix) }

        private fun runTestWithFile(fileName: String, testWithFile: (String) -> Unit): Unit {
            try {
                testWithFile(fileName)
            } catch (ex: Exception) {
                fail("Exception occured: $ex")
            }
        }

        private fun createTempDir(): File = File.createTempFile("temp", System.currentTimeMillis().toString()).also {
            it.delete()
            it.mkdir()
            it.deleteOnExit()
        }
    }
}
