package com.waz.zclient.user.domain.usecase.phonenumber

import com.waz.zclient.core.exception.FeatureFailure

object CountryCodeInvalid : ValidatePhoneNumberError()
object PhoneNumberInvalid : ValidatePhoneNumberError()

sealed class ValidatePhoneNumberError : FeatureFailure()
