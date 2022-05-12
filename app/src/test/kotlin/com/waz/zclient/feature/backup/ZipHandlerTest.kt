package com.waz.zclient.feature.backup

import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.feature.backup.zip.NoFilesToZipFailure
import com.waz.zclient.feature.backup.zip.ZipHandler
import com.waz.zclient.feature.createTextFile
import com.waz.zclient.feature.uniqueZipFileName
import org.amshove.kluent.`should be greater than`
import org.amshove.kluent.shouldEqual
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ZipHandlerTest : UnitTest() {

    @Test
    fun `given a non-empty input text file, when zipped, then return a zipped non-empty file`() {
        val tempDir = createTempDir()
        val textFile = createTextFile(tempDir)
        textFile.length() `should be greater than` 0

        val zipHandler = ZipHandler(tempDir)

        zipHandler.zip(uniqueZipFileName(), listOf(textFile))
            .onSuccess { it.length() `should be greater than` 0 }
            .onFailure { fail(it.toString()) }
    }

    @Test
    fun `given no input files, when zipped, then return a zip failure`() {
        val tempDir = createTempDir()

        val zipHandler = ZipHandler(tempDir)

        zipHandler.zip(uniqueZipFileName(), emptyList())
            .onSuccess { fail("The test should fail with ZipFailure") }
            .onFailure { assertTrue(it is NoFilesToZipFailure) }
    }

    @Test
    fun `given a list of non-empty input text files, when zipped, then return a zipped non-empty file`() {
        val tempDir = createTempDir()
        val fileList = listOf(createTextFile(tempDir), createTextFile(tempDir), createTextFile(tempDir))

        val zipHandler = ZipHandler(tempDir)

        zipHandler.zip(uniqueZipFileName(), fileList)
            .onSuccess { it.length() `should be greater than` 0 }
            .onFailure { fail(it.toString()) }
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

        zipHandler.unzip(zipFile)
            .onSuccess {
                it.size shouldEqual 1
                val unzippedContents = it.first().readText()
                unzippedContents shouldEqual originalContents
            }
            .onFailure { fail(it.toString()) }
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

        zipHandler.unzip(zipFile)
            .onSuccess {
                it.size shouldEqual 2
                val unzippedContents1 = it.first().readText()
                unzippedContents1 shouldEqual originalContents[it.first().name]
                val unzippedContents2 = it[1].readText()
                unzippedContents2 shouldEqual originalContents[it[1].name]
            }
            .onFailure { fail(it.toString()) }
    }
}
