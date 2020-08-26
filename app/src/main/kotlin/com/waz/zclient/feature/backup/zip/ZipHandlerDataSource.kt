package com.waz.zclient.feature.backup.zip

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.IOFailure
import com.waz.zclient.core.functional.Either
import java.io.File
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ZipHandlerDataSource(private val storageDir: File) : ZipHandler {

    override fun zip(zipFileName: String, files: List<File>): Either<Failure, File> {
        try {
            val zipFile = createFile(zipFileName)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
                files.map { writeZipEntry(zip, it) }
            }

            return Either.Right(zipFile)
        } catch (ex: IOException) {
            return Either.Left(IOFailure(ex))
        }
    }

    override fun unzip(zipFile: File): Either<Failure, List<File>> {
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
