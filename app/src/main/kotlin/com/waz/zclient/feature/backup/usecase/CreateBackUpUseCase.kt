package com.waz.zclient.feature.backup.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.feature.backup.BackUpRepository
import com.waz.zclient.feature.backup.crypto.encryption.EncryptionHandler
import com.waz.zclient.feature.backup.metadata.BackupMetaData
import com.waz.zclient.feature.backup.metadata.MetaDataHandler
import com.waz.zclient.feature.backup.zip.ZipHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.threeten.bp.Instant
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class CreateBackUpUseCase(
    private val backUpRepositories: List<BackUpRepository<List<File>>>,
    private val zipHandler: ZipHandler,
    private val encryptionHandler: EncryptionHandler,
    private val metaDataHandler: MetaDataHandler,
    private val backUpVersion: Int,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : UseCase<File, CreateBackUpUseCaseParams> {

    override suspend fun run(params: CreateBackUpUseCaseParams): Either<Failure, File> =
        backUpOrFail()
            .flatMap { files ->
                val metaData = BackupMetaData(
                    userId = params.userId,
                    clientId = params.clientId,
                    userHandle = params.userHandle,
                    backUpVersion = backUpVersion
                )
                metaDataHandler.generateMetaDataFile(metaData).map { files + it }
            }
            .flatMap { files ->
                zipHandler.zip(backupFileName(params.userHandle) + ".zip", files)
            }
            .flatMap { file ->
                encryptionHandler.encryptBackup(file, params.userId, params.password, backupFileName(params.userHandle))
            }

    private suspend fun backUpOrFail(): Either<Failure, List<File>> = extractFiles(
        backUpRepositories
            .map { coroutineScope.async(Dispatchers.IO) { it.saveBackup() } }
            .awaitAll()
    )

    @SuppressWarnings("MagicNumber")
    private fun backupFileName(userHandle: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Instant.now().epochSecond * 1000)
        return "Wire-$userHandle-Backup_$timestamp.android_wbu"
    }

    private fun extractFiles(filesOrFailure: List<Either<Failure, List<File>>>): Either<Failure, List<File>> {
        val files = mutableListOf<File>()

        filesOrFailure.forEach {
            when (it) {
                is Either.Right -> files.addAll(it.b)
                is Either.Left -> return Either.Left(it.a)
            }
        }

        return Either.Right(files.toList())
    }
}

data class CreateBackUpUseCaseParams(val userId: String, val clientId: String, val userHandle: String, val password: String)
