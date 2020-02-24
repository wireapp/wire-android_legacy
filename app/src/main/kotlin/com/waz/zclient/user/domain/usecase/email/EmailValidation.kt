package com.waz.zclient.user.domain.usecase.email

import com.waz.zclient.core.exception.FeatureFailure


object EmailTooShort : ValidateEmailError()
object EmailInvalid : ValidateEmailError()
object EmailValid : ValidateEmailSuccess()

sealed class ValidateEmailSuccess
sealed class ValidateEmailError : FeatureFailure()
