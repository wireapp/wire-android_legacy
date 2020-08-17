package com.waz.zclient.feature.backup.io.file

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.exception.IOFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.utilities.converters.JsonConverter
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.feature.backup.io.BatchReader
import com.waz.zclient.feature.backup.io.mapRight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

class BackUpFileIOHandler<T>(
    private val fileNamePrefix: String,
    private val jsonConverter: JsonConverter<T>,
    private val targetDir: File
) : BackUpIOHandler<T, File> {
    override suspend fun write(iterator: BatchReader<List<T>>) = withContext(Dispatchers.IO) {
        var index = 0

        iterator.mapRight {
            try {
                val file = getFile(index).also {
                    it.delete()
                    it.createNewFile()
                    it.deleteOnExit()
                }
                val jsonStr = jsonConverter.toJsonList(it)
                file.writeText(jsonStr)
                ++index
                Either.Right(file)
            } catch (ex: IOException) {
                Either.Left(IOFailure(ex))
            } catch (ex: SerializationException) {
                Either.Left(SerializationFailure(ex))
            }
        }
    }

    override fun readIterator() = object : BatchReader<List<T>> {
        private var index = 0

        override suspend fun readNext(): Either<Failure, List<T>> =
            try {
                val file = getFile(index)
                if (file.exists()) {
                    val jsonStr = file.bufferedReader().readText()
                    Either.Right(jsonConverter.fromJsonList(jsonStr)).also { ++index }
                } else {
                    Either.Right(emptyList())
                }
            } catch (ex: IOException) {
                Either.Left(IOFailure(ex))
            } catch (ex: SerializationException) {
                Either.Left(SerializationFailure(ex))
            }

        override suspend fun hasNext(): Boolean = getFile(index).exists()
    }

    private fun getFile(index: Int): File =
        if (index == 0) File(targetDir, "$fileNamePrefix.json")
        else File(targetDir, "${fileNamePrefix}_$index.json")
}

data class SerializationFailure(val ex: SerializationException) : FeatureFailure()
