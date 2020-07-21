package com.waz.zclient.core.utilities

import android.os.Environment
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object IOUtils {
    private const val bufferSize = 8096

    data class IOFailure(val exception: IOException) : FeatureFailure()

    private val buffer = object : ThreadLocal<ByteArray>() {
        override fun initialValue(): ByteArray = ByteArray(bufferSize)
    }

    fun <Resource : Closeable, Output> withResource(resource: Resource, fn: (Resource) -> Output): Either<Failure, Output> =
        try {
            Either.Right(fn(resource))
        } catch (ex: IOException) {
            Either.Left(IOFailure(ex))
        } finally {
            resource.close()
        }

    fun createTemporaryFolder(name: String = "temp_${System.currentTimeMillis()}", deleteIfExists: Boolean = true): Either<Failure, File> =
        try {
            val file = File("${Environment.getExternalStorageDirectory()}/$name").apply {
                if (deleteIfExists) {
                    delete()
                }
                if (!exists()) {
                    mkdir()
                }
                deleteOnExit()
            }
            Either.Right(file)
        } catch (ex: IOException) {
            Either.Left(IOFailure(ex))
        }

    fun writeTextToFile(targetDir: File, fileName: String, text: () -> String): Either<Failure, File> =
        try {
            val file = File(targetDir, fileName).apply {
                if (exists()) {
                    delete()
                }
                createNewFile()
                writeText(text())
            }
            Either.Right(file)
        } catch (ex: IOException) {
            Either.Left(IOFailure(ex))
        }

    fun writeBytesToFile(targetDir: File, fileName: String, bytes: () -> ByteArray): Either<Failure, File> =
        try {
            val file = File(targetDir, fileName).apply {
                if (exists()) {
                    delete()
                }
                createNewFile()
                val stream = BufferedOutputStream(FileOutputStream(this))
                stream.write(bytes())
            }
            Either.Right(file)
        } catch (ex: IOException) {
            Either.Left(IOFailure(ex))
        }

    // this method assumes that both streams will be properly closed outside
    fun writeZipEntry(inStream: InputStream, zip: ZipOutputStream, entryName: String) {
        zip.putNextEntry(ZipEntry(entryName))
        write(inStream, zip)
        zip.closeEntry()
    }

    // this method assumes that both streams will be properly closed outside
    fun write(inStream: InputStream, outStream: OutputStream, buff: ByteArray = buffer.get() ?: ByteArray(bufferSize)) {
        do {
            val bytesRead = inStream.read(buff)
            if (bytesRead > 0) {
                outStream.write(buff, 0, bytesRead)
            }
        } while (bytesRead > 0)
    }

    fun readBytesFromFile(file: File, offset: Int = 0, outputSize: Int = file.length().toInt() - offset): Either<Failure, ByteArray> =
        withResource(BufferedInputStream(FileInputStream(file))) {
            val bytes = ByteArray(outputSize)
            it.skip(offset.toLong())
            it.read(bytes)
            bytes
        }
}
