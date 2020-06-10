package com.waz.zclient.shared.countrycode

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.waz.zclient.UnitTest
import com.waz.zclient.core.config.DeveloperOptionsConfig
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.shared.countrycode.usecase.GetCountryCodesParams
import com.waz.zclient.shared.countrycode.usecase.GetCountryCodesUseCase
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsCollectionContaining.hasItem
import org.hamcrest.core.IsNot.not
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
    private lateinit var developerOptionsConfig: DeveloperOptionsConfig

    @Mock
    private lateinit var getCountryCodeParams: GetCountryCodesParams

    @Before
    fun setup() {
        Mockito.`when`(phoneNumberUtil.supportedRegions).thenReturn(mockListOfRegions())
        Mockito.`when`(getCountryCodeParams.deviceLanguage).thenReturn(TEST_LANGUAGE)
        getCountriesCodesUseCase = GetCountryCodesUseCase(phoneNumberUtil, developerOptionsConfig)
    }

    @Test
    fun `given loaded countries is executed, when developer options is off, then return list of countries without QA`() = runBlockingTest {
        Mockito.`when`(developerOptionsConfig.isDeveloperSettingsEnabled).thenReturn(false)

        getCountriesCodesUseCase.run(getCountryCodeParams).onSuccess {
            assertEquals(5, it.size)
            assertThat(it, not(hasItem(TEST_QA_COUNTRY)))
        }
    }

    @Test
    fun `given loaded countries is executed, when developer options is on, then return list of countries with QA`() = runBlockingTest {
        Mockito.`when`(developerOptionsConfig.isDeveloperSettingsEnabled).thenReturn(true)

        getCountriesCodesUseCase.run(getCountryCodeParams).onSuccess {
            assertEquals(6, it.size)
            assertEquals(TEST_QA_COUNTRY, it[0].country)
            assertEquals(TEST_QA_COUNTRY_CODE, it[0].countryCode)
            assertEquals(TEST_QA_DISPLAY_COUNTRY, it[0].countryDisplayName)
        }
    }

    private fun mockListOfRegions(): MutableSet<String>? =
        mutableSetOf("GER", "GB", "FR", "IT", "SP")

    companion object {
        private const val TEST_LANGUAGE = "en-gb"
        private const val TEST_QA_COUNTRY = "QA-code"
        private const val TEST_QA_DISPLAY_COUNTRY = "QA-Shortcut"
        private const val TEST_QA_COUNTRY_CODE = "+0"
    }


}
