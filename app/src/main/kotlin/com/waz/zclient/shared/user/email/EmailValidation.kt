package com.waz.zclient.shared.user.email

import com.waz.zclient.core.exception.FeatureFailure

object EmailTooShort : ValidateEmailError()
object EmailInvalid : ValidateEmailError()

sealed class ValidateEmailError : FeatureFailure()
