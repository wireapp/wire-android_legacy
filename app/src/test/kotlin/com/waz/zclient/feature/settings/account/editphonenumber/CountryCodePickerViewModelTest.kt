package com.waz.zclient.feature.settings.account.editphonenumber

import com.waz.zclient.UnitTest
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.shared.user.phonenumber.Country
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.lenient

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
        runBlockingTest {
            val params = GetCountryCodesParams(TEST_LANGUAGE)
            val countries = listOf(country, country, country)
            lenient().`when`(getCountryCodesUseCase.run(params)).thenReturn(Either.Right(countries))

            countryCodePickerViewModel.loadCountries(TEST_LANGUAGE)

            countryCodePickerViewModel.countriesLiveData.observeOnce {
                it shouldBe countries
                it.size shouldBe 3
            }
        }

    @Test
    fun `given country code is updated, when country code isn't the same as the one in db, return new country`() =
        runBlockingTest {
            countryCodePickerViewModel.onCountryCodeChanged(TEST_COUNTRY, FRANCE_DISPLAY_NAME)

            countryCodePickerViewModel.selectedCountryLiveData.observeOnce {
                it shouldBe TEST_COUNTRY
            }
            countryCodePickerViewModel.dismissLiveData.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given country code is updated, when country code is the same as the one in db, return empty country`() =
        runBlockingTest {
            countryCodePickerViewModel.onCountryCodeChanged(TEST_COUNTRY, GERMANY_DISPLAY_NAME)

            countryCodePickerViewModel.dismissLiveData.observeOnce {
                it shouldBe Unit
            }
        }


    companion object {
        private const val TEST_LANGUAGE = "en-gb"
        private const val GERMANY_DISPLAY_NAME = "Germany"
        private const val FRANCE_DISPLAY_NAME = "France"

        private val TEST_COUNTRY = Country(String.empty(), GERMANY_DISPLAY_NAME, String.empty())
    }
}
