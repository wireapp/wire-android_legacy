package com.waz.zclient.feature.backup

import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.Either
import org.amshove.kluent.`should be greater than`
import org.amshove.kluent.shouldEqual
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Base64
import kotlin.random.Random

class ZipHandlerTest : UnitTest() {

    private fun generateText(length: Int): String = Base64.getEncoder().encodeToString(Random.Default.nextBytes(length))

    private fun createTempDir(): File = File.createTempFile("temp", System.currentTimeMillis().toString()).apply {
        delete()
        mkdirs()
        deleteOnExit()
    }

    private fun uniqueTextFileName(): String {
        Thread.sleep(1)
        return "ZipHandlerTest_${System.currentTimeMillis()}.txt"
    }

    private fun uniqueZipFileName(): String {
        Thread.sleep(1)
        return "ZipHandlerTest_${System.currentTimeMillis()}.zip"
    }

    private fun createTextFile(dir: File, length: Int = 100): File =
            File(dir, uniqueTextFileName()).apply {
                bufferedWriter().use { it.write(generateText(length)) }
            }

    @Test
    fun `given a non-empty input text file, when zipped, then return a zipped non-empty file`() {
        val tempDir = createTempDir()
        val textFile = createTextFile(tempDir)
        textFile.length() `should be greater than` 0

        val zipHandler = ZipHandler(tempDir)

        when (val res = zipHandler.zip(uniqueZipFileName(), listOf(textFile))) {
            is Either.Left -> fail(res.a.toString())
            is Either.Right -> {
                val zipFile = res.b
                zipFile.length() `should be greater than` 0
            }
        }
    }

    @Test
    fun `given no input files, when zipped, then return a zipped file`() {
        val tempDir = createTempDir()

        val zipHandler = ZipHandler(tempDir)

        when (val res = zipHandler.zip(uniqueZipFileName(), emptyList())) {
            is Either.Left -> fail(res.a.toString())
            is Either.Right -> {
                val zipFile = res.b
                zipFile.length() `should be greater than` 0
            }
        }
    }

    @Test
    fun `given a list of non-empty input text files, when zipped, then return a zipped non-empty file`() {
        val tempDir = createTempDir()
        val fileList = listOf(createTextFile(tempDir), createTextFile(tempDir), createTextFile(tempDir))

        val zipHandler = ZipHandler(tempDir)

        when (val res = zipHandler.zip(uniqueZipFileName(), fileList)) {
            is Either.Left -> fail(res.a.toString())
            is Either.Right -> {
                val zipFile = res.b
                zipFile.length() `should be greater than` 0
            }
        }
    }

    @Test
    fun `given a non-empty input text file, when zipped and unzipped, then return a file with the same contents`() {
        val tempDir = createTempDir()
        val originalFile = createTextFile(tempDir)
        val originalContents = originalFile.readText()

        val zipHandler = ZipHandler(tempDir)

        val zipped = zipHandler.zip(uniqueZipFileName(), listOf(originalFile))
        assert(zipped.isRight)
        val zipFile = (zipped as Either.Right<File>).b

        when (val res = zipHandler.unzip(zipFile)) {
            is Either.Left -> fail(res.a.toString())
            is Either.Right -> {
                val unzipped = res.b
                unzipped.size shouldEqual 1
                val unzippedContents = unzipped.first().readText()
                unzippedContents shouldEqual originalContents
            }
        }
    }

    @Test
    fun `given a list of non-empty input text files, when zipped and unzipped, then return files with the same contents`() {
        val tempDir = createTempDir()
        val originalFile1 = createTextFile(tempDir)
        val originalContents1 = originalFile1.readText()
        val originalFile2 = createTextFile(tempDir)
        val originalContents2 = originalFile2.readText()
        val originalContents = mapOf(
                originalFile1.name to originalContents1,
                originalFile2.name to originalContents2
        )

        val zipHandler = ZipHandler(tempDir)

        val zipped = zipHandler.zip(uniqueZipFileName(), listOf(originalFile1, originalFile2))
        assert(zipped.isRight)
        val zipFile = (zipped as Either.Right<File>).b

        when (val res = zipHandler.unzip(zipFile)) {
            is Either.Left -> fail(res.a.toString())
            is Either.Right -> {
                val unzipped = res.b
                unzipped.size shouldEqual 2
                val unzippedContents1 = unzipped.first().readText()
                unzippedContents1 shouldEqual originalContents[unzipped.first().name]
                val unzippedContents2 = unzipped[1].readText()
                unzippedContents2 shouldEqual originalContents[unzipped[1].name]
            }
        }
    }
}
