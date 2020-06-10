package com.waz.zclient.shared.countrycode

import com.waz.zclient.UnitTest
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.countrycode.usecase.GetCountryCodesParams
import com.waz.zclient.shared.countrycode.usecase.GetCountryCodesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class CountryCodePickerViewModelTest : UnitTest() {

    private lateinit var countryCodePickerViewModel: CountryCodePickerViewModel

    @Mock
    private lateinit var getCountryCodesUseCase: GetCountryCodesUseCase

    @Mock
    private lateinit var country: Country

    @Before
    fun setup() {
        countryCodePickerViewModel = CountryCodePickerViewModel(getCountryCodesUseCase)
    }

    @Test
    fun `given loaded countries is executed, when get country codes is successful, then return list of countries`() =
        runBlocking {
            val params = GetCountryCodesParams(TEST_LANGUAGE)
            val countries = listOf(country, country, country)
            `when`(getCountryCodesUseCase.run(params)).thenReturn(Either.Right(countries))

            countryCodePickerViewModel.loadCountries(TEST_LANGUAGE)

            countryCodePickerViewModel.countriesLiveData.awaitValue().let {
                assertEquals(countries, it)
                assertEquals(3, it.size)
            }
        }

    @Test
    fun `given country code is updated, when country code isn't the same as the one in db, return new country`() =
        runBlocking {
            countryCodePickerViewModel.onCountryCodeChanged(TEST_COUNTRY, FRANCE_DISPLAY_NAME)

            countryCodePickerViewModel.dismissLiveData.awaitValue().let {
                assertEquals(TEST_COUNTRY, countryCodePickerViewModel.selectedCountryLiveData.value)
                assertEquals(Unit, it)
            }
        }

    @Test
    fun `given country code is updated, when country code is the same as the one in db, return empty country`() =
        runBlocking {
            countryCodePickerViewModel.onCountryCodeChanged(TEST_COUNTRY, GERMANY_DISPLAY_NAME)
            assertEquals(Unit, countryCodePickerViewModel.dismissLiveData.awaitValue())
        }


    companion object {
        private const val TEST_LANGUAGE = "en-gb"
        private const val GERMANY_DISPLAY_NAME = "Germany"
        private const val FRANCE_DISPLAY_NAME = "France"

        private val TEST_COUNTRY = Country(String.empty(), GERMANY_DISPLAY_NAME, String.empty())
    }
}
