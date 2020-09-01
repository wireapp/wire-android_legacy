package com.waz.zclient.feature.backup.metadata

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.IOFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.utilities.converters.JsonConverter
import com.waz.zclient.feature.backup.io.file.SerializationFailure
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

class MetaDataHandler(
    private val jsonConverter: JsonConverter<BackupMetaData>,
    private val targetDir: File
) {
    fun generateMetaDataFile(metaData: BackupMetaData): Either<Failure, File> =
        try {
            val jsonStr = jsonConverter.toJson(metaData)
            val file = File(targetDir, FILE_NAME).apply {
                delete()
                createNewFile()
                deleteOnExit()
                writeText(jsonStr)
            }
            Either.Right(file)
        } catch (ex: IOException) {
            Either.Left(IOFailure(ex))
        } catch (ex: SerializationException) {
            Either.Left(SerializationFailure(ex))
        }

    fun readMetaData(file: File): Either<Failure, BackupMetaData> =
        try {
            val jsonStr = file.bufferedReader().readText()
            Either.Right(jsonConverter.fromJson(jsonStr))
        } catch (ex: IOException) {
            Either.Left(IOFailure(ex))
        } catch (ex: SerializationException) {
            Either.Left(SerializationFailure(ex))
        }

    companion object {
        const val FILE_NAME: String = "metadata.json"
    }
}
