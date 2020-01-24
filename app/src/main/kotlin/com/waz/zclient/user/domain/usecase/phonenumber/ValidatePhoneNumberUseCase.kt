package com.waz.zclient.user.domain.usecase.phonenumber

import androidx.core.text.isDigitsOnly
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

object CountryCodeInvalid : ValidatePhoneNumberError()
object PhoneNumberInvalid : ValidatePhoneNumberError()

sealed class ValidatePhoneNumberError : FeatureFailure()

class ValidatePhoneNumberUseCase : UseCase<String, ValidatePhoneNumberParams>() {

    override suspend fun run(params: ValidatePhoneNumberParams): Either<Failure, String> =
        if (!isCountryCodeValid(params.countryCode)) {
            Either.Left(CountryCodeInvalid)
        } else if (!params.phoneNumber.isDigitsOnly()) {
            Either.Left(PhoneNumberInvalid)
        } else {
            val phoneNumber = "${params.countryCode}${params.phoneNumber}"
            if (isPhoneNumberValid(phoneNumber)) {
                Either.Left(PhoneNumberInvalid)
            } else {
                Either.Right(phoneNumber)
            }
        }

    //TODO determine if this is enough to cover all supported countries?
    private fun isPhoneNumberValid(phoneNumber: String) =
        phoneNumber.matches(PHONE_NUMBER_REGEX)

    private fun isCountryCodeValid(countryCode: String) =
        countryCode.matches(COUNTRY_CODE_REGEX)

    companion object {
        //Matches E.164 phone number format
        private val PHONE_NUMBER_REGEX = "^\\+?[1-9]\\d{1,14}\$".toRegex()
        private val COUNTRY_CODE_REGEX = "^(\\+?\\d{1,3}|\\d{1,4})\$".toRegex()
    }
}

data class ValidatePhoneNumberParams(
    val countryCode: String,
    val phoneNumber: String
)
