package com.waz.zclient.user.domain.usecase.handle

import com.waz.zclient.core.exception.FeatureFailure

object HandleTooLongError : ValidateHandleError()
object HandleTooShortError : ValidateHandleError()
object HandleInvalidError : ValidateHandleError()
object HandleExistsAlreadyError : ValidateHandleError()
object HandleUnknownError : ValidateHandleError()
object HandleIsAvailable : ValidateHandleSuccess()

sealed class ValidateHandleSuccess
sealed class ValidateHandleError : FeatureFailure()
