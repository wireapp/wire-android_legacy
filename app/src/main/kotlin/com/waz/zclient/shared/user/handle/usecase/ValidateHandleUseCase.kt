package com.waz.zclient.shared.user.handle.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.shared.user.handle.HandleInvalid
import com.waz.zclient.shared.user.handle.HandleTooLong
import com.waz.zclient.shared.user.handle.HandleTooShort

class ValidateHandleUseCase : UseCase<String, ValidateHandleParams>() {

    override suspend fun run(params: ValidateHandleParams): Either<Failure, String> =
        if (!handleCharactersValid(params.newHandle)) {
            Either.Left(HandleInvalid)
        } else {
            when {
                isHandleTooLong(params.newHandle) -> Either.Left(HandleTooLong)
                isHandleTooShort(params.newHandle) -> Either.Left(HandleTooShort)
                else -> Either.Right(params.newHandle)
            }
        }

    private fun handleCharactersValid(handle: String) =
        handle.matches(HANDLE_REGEX)

    private fun isHandleTooLong(handle: String) =
        handle.length > HANDLE_MAX_LENGTH

    private fun isHandleTooShort(handle: String) =
        handle.length < HANDLE_MIN_LENGTH

    companion object {
        private const val HANDLE_MAX_LENGTH = 21
        private const val HANDLE_MIN_LENGTH = 2
        private val HANDLE_REGEX = """^([a-z]|[0-9]|_)*""".toRegex()
    }
}

data class ValidateHandleParams(val newHandle: String)
