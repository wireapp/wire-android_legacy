package com.waz.zclient.feature.backup.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.feature.backup.BackUpRepository

class CreateBackUpUseCase(private val backUpRepositories: List<BackUpRepository>) : UseCase<Unit, Unit> {

    override suspend fun run(params: Unit): Either<Failure, Unit> {
        var failure = false
        //TODO we can speed it up by async + await maybe?
        for (repo in backUpRepositories) {
            if (repo.backUp().isLeft) {
                failure = true
                break
            }
        }
        //TODO would be nice to log the actual exception somewhere
        //TODO rollback changes if something goes wrong
        return if (failure) Either.Left(BackUpCreationFailure) else Either.Right(Unit)
    }
}

object BackUpCreationFailure : FeatureFailure()
