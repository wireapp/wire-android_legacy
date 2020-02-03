package com.waz.zclient.user.domain.usecase.phonenumber

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase

class CountryCodeAndPhoneNumberUseCase(private val phoneNumberUtil: PhoneNumberUtil) :
    UseCase<PhoneNumber, CountryCodeAndPhoneNumberParams>() {

    override suspend fun run(params: CountryCodeAndPhoneNumberParams): Either<Failure, PhoneNumber> {
        val number = params.phoneNumber
        return if (number.isNotEmpty()) {
            val validatedNumber: String? = if (number.startsWith("+")) number else "+$number"

            val phoneNumber: Phonenumber.PhoneNumber = try {
                phoneNumberUtil.parse(validatedNumber, GERMAN_REGION_CODE)
            } catch (e: NumberParseException) {
                null
            } ?: return Either.Left(PhoneNumberInvalid)

            val regionCountryCode = phoneNumberUtil.getRegionCodeForCountryCode(phoneNumber.countryCode)
            val countryCode = "+${phoneNumberUtil.getCountryCodeForRegion(regionCountryCode)}"
            val numberWithoutCountryCode = number.removePrefix(countryCode)
            Either.Right(PhoneNumber(countryCode, numberWithoutCountryCode))
        } else {
            Either.Left(PhoneNumberInvalid)
        }
    }

    companion object {
        private const val GERMAN_REGION_CODE = "GER"
    }
}

data class PhoneNumber(val countryCode: String, val number: String)
data class CountryCodeAndPhoneNumberParams(val phoneNumber: String)
