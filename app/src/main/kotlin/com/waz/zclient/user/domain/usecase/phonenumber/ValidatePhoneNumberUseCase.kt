package com.waz.zclient.user.domain.usecase.phonenumber

import androidx.core.text.isDigitsOnly
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

class ValidatePhoneNumberUseCase : UseCase<String, ValidatePhoneNumberParams>() {

    override suspend fun run(params: ValidatePhoneNumberParams) =
        when (!isCountryCodeValid(params.countryCode)) {
            true -> Either.Left(CountryCodeInvalid)
            else -> {
                when (!params.phoneNumber.isDigitsOnly()) {
                    true -> Either.Left(PhoneNumberInvalid)
                    else -> {
                        val phoneNumber = "${params.countryCode}${params.phoneNumber}"
                        when (!isPhoneNumberValid(phoneNumber)) {
                            true -> Either.Left(PhoneNumberInvalid)
                            else -> Either.Right(phoneNumber)
                        }
                    }
                }
            }
        }

    //TODO determine if this is enough to cover all supported countries?
    private fun isPhoneNumberValid(phoneNumber: String) =
        phoneNumber.matches(PHONE_NUMBER_REGEX) || phoneNumber.isEmpty()

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
