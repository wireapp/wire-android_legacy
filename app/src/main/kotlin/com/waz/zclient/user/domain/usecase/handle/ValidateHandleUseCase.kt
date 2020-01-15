package com.waz.zclient.user.domain.usecase.handle

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

class ValidateHandleUseCase : UseCase<String, ValidateHandleParams>() {

    override suspend fun run(params: ValidateHandleParams): Either<Failure, String> =
        if (!handleCharactersValid(params.newHandle)) {
            Either.Left(HandleInvalidError)
        } else {
            when {
                params.newHandle.length > HANDLE_MAX_LENGTH -> Either.Left(HandleTooLongError)
                params.newHandle.length < HANDLE_MIN_LENGTH -> Either.Left(HandleTooShortError)
                else -> Either.Right(params.newHandle)
            }
        }

    private fun handleCharactersValid(handle: String) =
        handle.matches(HANDLE_REGEX)

    companion object {
        private const val HANDLE_MAX_LENGTH = 21
        private const val HANDLE_MIN_LENGTH = 2
        private val HANDLE_REGEX = """^([a-z]|[0-9]|_)*""".toRegex()
    }
}

data class ValidateHandleParams(val newHandle: String)
