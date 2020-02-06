package com.waz.zclient.settings.account.editphonenumber

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.usecase.UseCase
import com.waz.zclient.user.domain.usecase.phonenumber.Country
import java.util.Locale

class GetCountryCodesUseCase(
    private val phoneNumberUtils: PhoneNumberUtil,
    private val developerOptionsEnabled: Boolean
) : UseCase<List<Country>, GetCountryCodesParams>() {

    override suspend fun run(params: GetCountryCodesParams): Either<Failure, List<Country>> {
        val countries = mutableListOf<Country>()
        phoneNumberUtils.supportedRegions.forEach {
            if (developerOptionsEnabled) {
                val qaCountry = Country(QA_COUNTRY, QA_DISPLAY_COUNTRY, QA_COUNTRY_CODE)
                countries.add(0, qaCountry)
            }

            val locale = Locale(params.deviceLanguage, it)
            val countryCode = phoneNumberUtils.getCountryCodeForRegion(it)
            val country = Country(locale.country, locale.displayCountry, "+${countryCode}")
            countries.add(country)
        }
        return Either.Right(countries)
    }

    companion object {
        private const val QA_COUNTRY = "QA-code"
        private const val QA_DISPLAY_COUNTRY = "QA-Shortcut"
        private const val QA_COUNTRY_CODE = "+0"
    }
}

data class GetCountryCodesParams(val deviceLanguage: String)
