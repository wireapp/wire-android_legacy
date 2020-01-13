package com.waz.zclient.user.domain.usecase.handle

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.getOrElse
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.data.UsersRepository

object HandleTooLongError : ValidateHandleState()
object HandleTooShortError : ValidateHandleState()
object HandleInvalidError : ValidateHandleState()
object HandleExistsAlreadyError : ValidateHandleState()

sealed class ValidateHandleState : FeatureFailure()

class ValidateHandleUseCase(private val usersRepository: UsersRepository)
    : UseCase<String, ValidateHandleParams>() {

    companion object {
        private const val HANDLE_MAX_LENGTH = 21
        private const val HANDLE_MIN_LENGTH = 2
        private val HANDLE_REGEX = """^([a-z]|[0-9]|_)*""".toRegex()
    }

    override suspend fun run(params: ValidateHandleParams): Either<Failure, String> {
        val newHandle = params.newHandle
        val handleAvailable = usersRepository.doesHandleExist(newHandle).getOrElse(true)
        return if (handleAvailable) {
            isHandleValid(newHandle)
        } else {
            //TODO Don't like this, it'll never hit but won't let me return otherwise.
            Either.Left(HandleExistsAlreadyError)
        }
    }

    private fun isHandleValid(newHandle: String): Either<Failure, String> {
        if (newHandle.matches(HANDLE_REGEX)) {
            if (newHandle.length > HANDLE_MAX_LENGTH) {
                return Either.Left(HandleTooLongError)
            } else if (newHandle.length < HANDLE_MIN_LENGTH) {
                return Either.Left(HandleTooShortError)
            }
        } else {
            return Either.Left(HandleInvalidError)
        }
        return Either.Right(newHandle)
    }
}

data class ValidateHandleParams(val newHandle: String)
