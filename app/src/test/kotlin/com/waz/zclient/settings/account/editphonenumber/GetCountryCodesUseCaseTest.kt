package com.waz.zclient.settings.account.editphonenumber

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.waz.zclient.UnitTest
import com.waz.zclient.core.functional.onSuccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldNotContain
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito

@ExperimentalCoroutinesApi
class GetCountryCodesUseCaseTest : UnitTest() {

    private lateinit var getCountriesCodesUseCase: GetCountryCodesUseCase

    @Mock
    private lateinit var phoneNumberUtil: PhoneNumberUtil

    @Mock
    private lateinit var getCountryCodeParams: GetCountryCodesParams

    @Before
    fun setup() {
        Mockito.`when`(phoneNumberUtil.supportedRegions).thenReturn(mockListOfRegsions())
        Mockito.`when`(getCountryCodeParams.deviceLanguage).thenReturn(TEST_LANGUAGE)
    }

    @Test
    fun `given loaded countries is executed, when developer options is off, then return list of countries without QA`() = runBlockingTest {
        getCountriesCodesUseCase = GetCountryCodesUseCase(phoneNumberUtil, false)

        getCountriesCodesUseCase.run(getCountryCodeParams).onSuccess {
            it.size shouldBe 5
            it.shouldNotContain(TEST_QA_COUNTRY)
        }
    }

    @Test
    fun `given loaded countries is executed, when developer options is on, then return list of countries with QA`() = runBlockingTest {
        getCountriesCodesUseCase = GetCountryCodesUseCase(phoneNumberUtil, true)

        getCountriesCodesUseCase.run(getCountryCodeParams).onSuccess {
            it.size shouldBe 6
            it[0].country shouldBe TEST_QA_COUNTRY
            it[0].countryCode shouldBe TEST_QA_COUNTRY_CODE
            it[0].countryDisplayName shouldBe TEST_QA_DISPLAY_COUNTRY
        }
    }

    private fun mockListOfRegsions(): MutableSet<String>? {
        return mutableSetOf("GER", "GB", "FR", "IT", "SP")
    }

    companion object {
        private const val TEST_LANGUAGE = "en-gb"
        private const val TEST_QA_COUNTRY = "QA-code"
        private const val TEST_QA_DISPLAY_COUNTRY = "QA-Shortcut"
        private const val TEST_QA_COUNTRY_CODE = "+0"
    }


}
