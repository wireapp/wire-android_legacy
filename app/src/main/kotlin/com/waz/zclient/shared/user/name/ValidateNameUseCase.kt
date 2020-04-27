package com.waz.zclient.shared.user.name

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

class ValidateNameUseCase : UseCase<Unit, ValidateNameParams>() {

    override suspend fun run(params: ValidateNameParams): Either<Failure, Unit> =
        when {
            isNameTooShort(params.name) -> Either.Left(NameTooShort)
            else -> Either.Right(Unit)
        }

    private fun isNameTooShort(name: String) = name.trim().length <= NAME_MIN_LENGTH

    companion object {
        private const val NAME_MIN_LENGTH = 1
    }
}

data class ValidateNameParams(val name: String)

object NameTooShort : ValidateNameFailure()

sealed class ValidateNameFailure : FeatureFailure()
