package com.waz.zclient.feature.backup.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.feature.backup.BackUpRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

import java.io.File

class CreateBackUpUseCase(
    private val backUpRepositories: List<BackUpRepository<File>>,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : UseCase<Unit, Unit> {

    //TODO would be nice to log the actual exception somewhere
    //TODO rollback changes if something goes wrong
    override suspend fun run(params: Unit): Either<Failure, Unit> =
        if (hasBackupFailed()) Either.Left(BackUpCreationFailure) else Either.Right(Unit)

    private suspend fun hasBackupFailed(): Boolean =
        backUpRepositories
            .map { coroutineScope.async(Dispatchers.IO) { it.saveBackup() } }
            .awaitAll()
            .any { it.isLeft }
}

object BackUpCreationFailure : FeatureFailure()
