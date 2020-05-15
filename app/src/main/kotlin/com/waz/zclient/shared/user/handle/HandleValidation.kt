package com.waz.zclient.shared.user.handle

import com.waz.zclient.core.exception.FeatureFailure

object HandleTooLong : ValidateHandleError()
object HandleTooShort : ValidateHandleError()
object HandleInvalid : ValidateHandleError()
object HandleAlreadyExists : ValidateHandleError()
object HandleSameAsCurrent : ValidateHandleError()
object HandleIsAvailable : ValidateHandleSuccess()
object UnknownError : ValidateHandleError()

sealed class ValidateHandleSuccess
sealed class ValidateHandleError : FeatureFailure()
