package com.waz.zclient.feature.backup.io.file

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.IOFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.backup.BackUpIOHandler
import com.waz.zclient.feature.backup.io.BatchReader
import com.waz.zclient.feature.backup.io.forEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.File

class BackUpFileIOHandler<T>(
    private val fileName: String,
    private val jsonConverter: JsonConverter<T>
) : BackUpIOHandler<T> {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun write(iterator: BatchReader<T>): Either<Failure, Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(fileName).also {
                it.delete()
                it.createNewFile()
            }

            iterator.forEach {
                val jsonStr = jsonConverter.toJson(it)
                file.appendText("$jsonStr\n")
                Either.Right(Unit)
            }
        } catch (ex: Exception) {
            Either.Left(IOFailure(ex))
        }
    }

    override fun readIterator() = object : BatchReader<T> {
        private val reader by lazy { File(fileName).bufferedReader() }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun readNext(): Either<Failure, T?> = withContext(Dispatchers.IO) {
            try {
                val jsonStr = reader.readLine()
                if (jsonStr == null) {
                    reader.close()
                }
                Either.Right(jsonConverter.fromJson(jsonStr))
            } catch (ex: Exception) {
                reader.close()
                Either.Left(IOFailure(ex))
            }
        }
    }
}

class JsonConverter<T>(private val serializer: KSerializer<T>) {
    private val json by lazy { Json(JsonConfiguration.Stable) }

    fun fromJson(jsonString: String): T = json.parse(serializer, jsonString)

    fun toJson(model: T): String = json.stringify(serializer, model)
}
