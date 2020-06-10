package com.waz.zclient.shared.user.phonenumber.usecase

import androidx.core.text.isDigitsOnly
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

class ValidatePhoneNumberUseCase : UseCase<String, ValidatePhoneNumberParams> {

    override suspend fun run(params: ValidatePhoneNumberParams): Either<Failure, String> =
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

object CountryCodeInvalid : ValidatePhoneNumberFailure()
object PhoneNumberInvalid : ValidatePhoneNumberFailure()

sealed class ValidatePhoneNumberFailure : FeatureFailure()
