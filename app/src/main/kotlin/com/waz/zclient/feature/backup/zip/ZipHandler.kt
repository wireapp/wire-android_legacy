package com.waz.zclient.feature.backup.zip

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.exception.IOFailure
import com.waz.zclient.core.functional.Either
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ZipHandler(private val storageDir: File) {
    fun zip(zipFileName: String, files: List<File>): Either<Failure, File> =
            if (files.isEmpty()) {
                Either.Left(ZipFailure("Nothing to zip, the list of files is empty"))
            } else {
                try {
                    val zipFile = createFile(zipFileName).apply {
                        writeZipEntries(this, files)
                    }
                    Either.Right(zipFile)
                } catch (ex: IOException) {
                    Either.Left(IOFailure(ex))
                }
            }

    fun unzip(zipFile: File): Either<Failure, List<File>> {
        val unzippedFiles = mutableListOf<File>()

        try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    unzippedFiles.add(readZipEntry(zip, entry))
                }
            }
        } catch (ex: ZipException) {
            return Either.Left(IOFailure(ex))
        }
        return Either.Right(unzippedFiles.toList())
    }

    private fun writeZipEntries(zipFile: File, files: List<File>): Unit =
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
                files.forEach {
                    writeZipEntry(zip, it)
                }
            }

    private fun createFile(name: String) =
            File(storageDir, name).apply {
                delete()
                createNewFile()
                deleteOnExit()
            }

    private fun writeZipEntry(zip: ZipOutputStream, file: File) =
            FileInputStream(file).use { inStream ->
                zip.putNextEntry(ZipEntry(file.name))
                inStream.copyTo(zip)
                zip.closeEntry()
            }

    private fun readZipEntry(zip: ZipFile, entry: ZipEntry): File =
            createFile(entry.name).also { outputFile ->
                zip.getInputStream(entry).use { input ->
                    outputFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
}

data class ZipFailure(val err: String) : FeatureFailure()
