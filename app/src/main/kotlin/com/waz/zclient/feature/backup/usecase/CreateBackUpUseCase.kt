package com.waz.zclient.feature.backup.usecase

import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.flatMap
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.feature.backup.BackUpRepository
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
    private val metaDataHandler: MetaDataHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : UseCase<File, CreateBackUpUseCaseParams> {

    override suspend fun run(params: CreateBackUpUseCaseParams): Either<Failure, File> =
        backUpOrFail()
            .flatMap { files ->
                metaDataHandler.generateMetaDataFile(params.userId, params.userHandle).map { files + it }
            }
            .flatMap { files ->
                zipHandler.zip(backupZipFileName(params.userHandle), files)
            }

    private suspend fun backUpOrFail(): Either<Failure, List<File>> = extractFiles(
        backUpRepositories
            .map { coroutineScope.async(Dispatchers.IO) { it.saveBackup() } }
            .awaitAll()
    )

    @SuppressWarnings("MagicNumber")
    private fun backupZipFileName(userHandle: String): String {
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

data class CreateBackUpUseCaseParams(val userId: UserId, val userHandle: String, val password: String)
