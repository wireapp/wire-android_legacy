package com.waz.zclient.feature.backup.usecase

import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.feature.backup.BackUpRepository
import com.waz.zclient.feature.backup.encryption.EncryptionHandler
import com.waz.zclient.feature.backup.metadata.MetaDataHandler
import com.waz.zclient.feature.backup.zip.ZipHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.io.File

class RestoreBackUpUseCase(
    private val backUpRepositories: List<BackUpRepository<List<File>>>,
    private val zipHandler: ZipHandler,
    private val encryptionHandler: EncryptionHandler,
    private val metaDataHandler: MetaDataHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : UseCase<Unit, RestoreBackUpUseCaseParams> {

    override suspend fun run(params: RestoreBackUpUseCaseParams): Either<Failure, Unit> =
        encryptionHandler.decrypt(params.file, params.userId, params.password).flatMap { file ->
            zipHandler.unzip(file)
        }.flatMap { files ->
            val metaDataFile = files.find { it.name == MetaDataHandler.FILENAME }
            if (metaDataFile == null) Either.Left(NoMetaDataFileFailure)
            else metaDataHandler.checkMetaData(metaDataFile, params.userId)
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
}

object NoMetaDataFileFailure : FeatureFailure()

data class RestoreBackUpUseCaseParams(val file: File, val userId: UserId, val password: String)
