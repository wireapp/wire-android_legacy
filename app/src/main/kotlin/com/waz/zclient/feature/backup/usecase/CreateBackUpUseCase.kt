package com.waz.zclient.feature.backup.usecase

import com.waz.model.UserId
import com.waz.zclient.core.exception.Failure
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.threeten.bp.Instant

import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class CreateBackUpUseCase(
    private val backUpRepositories: List<BackUpRepository<File>>,
    private val zipHandler: ZipHandler,
    private val encryptionHandler: EncryptionHandler,
    private val metaDataHandler: MetaDataHandler,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : UseCase<File, Triple<UserId, String, String>> {

    override suspend fun run(params: Triple<UserId, String, String>): Either<Failure, File> {
        val userId = params.first
        val userHandle = params.second
        val password = params.third

        return backUpOrFail()
            .flatMap { files -> metaDataHandler.generateMetaDataFile(userId, userHandle).map { files + it } }
            .flatMap { zipHandler.zip(backupZipFileName(userHandle), it) }
            .flatMap { encryptionHandler.encrypt(it, userId, password) }
    }

    private suspend fun backUpOrFail() = extractFiles(
        backUpRepositories.map { coroutineScope.async(Dispatchers.IO) { it.saveBackup() } }.awaitAll()
    )

    @SuppressWarnings("MagicNumber")
    private fun backupZipFileName(userHandle: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Instant.now().epochSecond * 1000)
        return "Wire-$userHandle-Backup_$timestamp.android_wbu"
    }

    private fun extractFiles(list: List<Either<Failure, File>>): Either<Failure, List<File>> {
        val files = mutableListOf<File>()

        for (value in list)
            when (value) {
                is Either.Right -> files += value.b
                is Either.Left -> return Either.Left(value.a)
            }

        return Either.Right(files.toList())
    }

    companion object {
        const val TAG = "CreateBackUpUseCase"
    }
}
