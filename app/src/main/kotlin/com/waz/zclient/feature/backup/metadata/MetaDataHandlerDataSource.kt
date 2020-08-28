package com.waz.zclient.feature.backup.metadata

import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.IOFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.utilities.converters.JsonConverter
import com.waz.zclient.feature.backup.io.file.SerializationFailure
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException

class MetaDataHandlerDataSource(
    private val backUpVersion: Int,
    private val jsonConverter: JsonConverter<BackupMetaData>,
    private val targetDir: File
) : MetaDataHandler {
    override fun generateMetaDataFile(userId: UserId, userHandle: String): Either<Failure, File> =
        try {
            val metaData = BackupMetaData(userId.str(), userHandle, backUpVersion)
            val jsonStr = jsonConverter.toJson(metaData)
            val file = File(targetDir, FILENAME).apply {
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

    override fun readMetaData(file: File): Either<Failure, BackupMetaData> =
        try {
            val jsonStr = file.bufferedReader().readText()
            Either.Right(jsonConverter.fromJson(jsonStr))
        } catch (ex: IOException) {
            Either.Left(IOFailure(ex))
        } catch (ex: SerializationException) {
            Either.Left(SerializationFailure(ex))
        }

    companion object {
        const val FILENAME: String = "metadata.json"
    }
}
