package com.waz.zclient.feature.backup.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.feature.backup.BackUpRepository
import com.waz.zclient.feature.backup.crypto.decryption.DecryptionHandler
import com.waz.zclient.feature.backup.metadata.MetaDataHandler
import com.waz.zclient.feature.backup.zip.ZipHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File

class RestoreBackUpUseCase(
    private val backUpRepositories: List<BackUpRepository<List<File>>>,
    private val zipHandler: ZipHandler,
    private val decryptionHandler: DecryptionHandler,
    private val metaDataHandler: MetaDataHandler,
    private val backUpVersion: Int,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : UseCase<Unit, RestoreBackUpUseCaseParams> {

    override suspend fun run(params: RestoreBackUpUseCaseParams): Either<Failure, Unit> =
        decryptionHandler.decryptBackup(params.file, params.userId, params.password).flatMap { file ->
            zipHandler.unzip(file)
        }.flatMap { files ->
            val metaDataFile = files.find { it.name == MetaDataHandler.FILE_NAME }
            if (metaDataFile == null) Either.Left(NoMetaDataFileFailure)
            else checkMetaData(metaDataFile, params.userId)
        }.flatMap {
            restore()
        }

    private fun restore(): Either<Failure, Unit> = runBlocking {
        val res: Either<Failure, Unit>? = backUpRepositories
            .map { coroutineScope.async(Dispatchers.IO) { it.restoreBackup().map { Unit } } }
            .awaitAll()
            .find { it.isLeft }
        res ?: Either.Right(Unit)
    }

    internal fun checkMetaData(metaDataFile: File, userId: String): Either<Failure, Unit> =
        metaDataHandler.readMetaData(metaDataFile).flatMap { metaData ->
            when {
                metaData.userId != userId -> {
                    Either.Left(UserIdInvalid)
                }
                metaData.backUpVersion != backUpVersion -> {
                    Either.Left(UnknownBackupVersion(metaData.backUpVersion))
                }
                else -> Either.Right(Unit)
            }
        }
}

object NoMetaDataFileFailure : FeatureFailure()
object UserIdInvalid : FeatureFailure()
data class UnknownBackupVersion(val backUpVersion: Int) : FeatureFailure()

data class RestoreBackUpUseCaseParams(val file: File, val userId: String, val password: String)
