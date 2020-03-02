package com.waz.zclient.user.usecase.phonenumber

import com.waz.zclient.core.exception.FeatureFailure

object CountryCodeInvalid : ValidatePhoneNumberError()
object PhoneNumberInvalid : ValidatePhoneNumberError()

sealed class ValidatePhoneNumberError : FeatureFailure()
