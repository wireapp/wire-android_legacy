package com.waz.zclient.user.domain.usecase.email

import com.waz.zclient.core.exception.FeatureFailure

object EmailTooShort : ValidateEmailError()
object EmailInvalid : ValidateEmailError()

sealed class ValidateEmailError : FeatureFailure()
