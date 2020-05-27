package com.waz.zclient.feature.settings.account.editphonenumber

import com.waz.zclient.R
import com.waz.zclient.UnitTest
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.user.phonenumber.Country
import com.waz.zclient.shared.user.phonenumber.CountryCodeInvalid
import com.waz.zclient.shared.user.phonenumber.PhoneNumberInvalid
import com.waz.zclient.shared.user.phonenumber.usecase.ChangePhoneNumberParams
import com.waz.zclient.shared.user.phonenumber.usecase.ChangePhoneNumberUseCase
import com.waz.zclient.shared.user.phonenumber.usecase.CountryCodeAndPhoneNumberParams
import com.waz.zclient.shared.user.phonenumber.usecase.CountryCodeAndPhoneNumberUseCase
import com.waz.zclient.shared.user.phonenumber.usecase.DeletePhoneNumberUseCase
import com.waz.zclient.shared.user.phonenumber.usecase.PhoneNumber
import com.waz.zclient.shared.user.phonenumber.usecase.ValidatePhoneNumberParams
import com.waz.zclient.shared.user.phonenumber.usecase.ValidatePhoneNumberUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBe
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class SettingsAccountPhoneNumberViewModelTest : UnitTest() {

    @get:Rule
    val coroutinesTestRule = CoroutinesTestRule()

    private lateinit var settingsAccountPhoneNumberViewModel: SettingsAccountPhoneNumberViewModel

    @Mock
    private lateinit var changePhoneNumberUseCase: ChangePhoneNumberUseCase

    @Mock
    private lateinit var countryCodeAndPhoneNumberUseCase: CountryCodeAndPhoneNumberUseCase

    @Mock
    private lateinit var deletePhoneNumberUseCase: DeletePhoneNumberUseCase

    @Mock
    private lateinit var validatePhoneNumberUseCase: ValidatePhoneNumberUseCase

    @Before
    fun setup() {
        settingsAccountPhoneNumberViewModel = SettingsAccountPhoneNumberViewModel(
            validatePhoneNumberUseCase,
            changePhoneNumberUseCase,
            countryCodeAndPhoneNumberUseCase,
            deletePhoneNumberUseCase
        )
    }

    @Test
    fun `given country code and number have been entered, when country code fails validation, then update country code error`() = runBlocking {
        val params = ValidatePhoneNumberParams(TEST_COUNTRY_CODE, TEST_PHONE_NUMBER)
        `when`(validatePhoneNumberUseCase.run(params)).thenReturn(Either.Left(CountryCodeInvalid))

        settingsAccountPhoneNumberViewModel.afterNumberEntered(TEST_COUNTRY_CODE, TEST_PHONE_NUMBER)

        settingsAccountPhoneNumberViewModel.countryCodeErrorLiveData.awaitValue().let {
            assertEquals(it.errorMessage, R.string.edit_phone_dialog_country_code_error)
        }
    }

    @Test
    fun `given country code and number have been entered, when number fails validation, then update phone number error`() = runBlocking {
        val params = ValidatePhoneNumberParams(TEST_COUNTRY_CODE, TEST_PHONE_NUMBER)
        `when`(validatePhoneNumberUseCase.run(params)).thenReturn(Either.Left(PhoneNumberInvalid))

        settingsAccountPhoneNumberViewModel.afterNumberEntered(TEST_COUNTRY_CODE, TEST_PHONE_NUMBER)

        settingsAccountPhoneNumberViewModel.phoneNumberErrorLiveData.awaitValue().let {
            assertEquals(it.errorMessage, R.string.edit_phone_dialog_phone_number_error)
        }
    }

    @Test
    fun `given country code and number have been entered, when both are valid, then update confirmation data`() = runBlocking {
        val params = ValidatePhoneNumberParams(TEST_COUNTRY_CODE, TEST_PHONE_NUMBER)
        `when`(validatePhoneNumberUseCase.run(params)).thenReturn(Either.Right(TEST_COMPLETE_NUMBER))

        settingsAccountPhoneNumberViewModel.afterNumberEntered(TEST_COUNTRY_CODE, TEST_PHONE_NUMBER)

        settingsAccountPhoneNumberViewModel.confirmationLiveData.awaitValue().let {
            it shouldBe TEST_COMPLETE_NUMBER
        }
    }

    @Test
    fun `given phone number is loading, when country code is wrong, then update country code error`() = runBlocking {
        val params = CountryCodeAndPhoneNumberParams(TEST_COMPLETE_NUMBER, TEST_LANGUAGE)
        `when`(countryCodeAndPhoneNumberUseCase.run(params)).thenReturn(Either.Left(CountryCodeInvalid))

        settingsAccountPhoneNumberViewModel.loadPhoneNumberData(TEST_COMPLETE_NUMBER, TEST_LANGUAGE)

        settingsAccountPhoneNumberViewModel.countryCodeErrorLiveData.awaitValue().let {
            assertEquals(it.errorMessage, R.string.edit_phone_dialog_country_code_error)
        }
    }

    @Test
    fun `given phone number is loading, when phone number without country code is wrong, then update phone number error`() = runBlocking {
        val params = CountryCodeAndPhoneNumberParams(TEST_COMPLETE_NUMBER, TEST_LANGUAGE)
        `when`(countryCodeAndPhoneNumberUseCase.run(params)).thenReturn(Either.Left(PhoneNumberInvalid))

        settingsAccountPhoneNumberViewModel.loadPhoneNumberData(TEST_COMPLETE_NUMBER, TEST_LANGUAGE)

        settingsAccountPhoneNumberViewModel.phoneNumberErrorLiveData.awaitValue().let {
            assertEquals(it.errorMessage, R.string.edit_phone_dialog_phone_number_error)
        }
    }

    @Test
    fun `given phone number is loading, country code and phone is parsed correctly, then update confirmation data`() = runBlocking {
        val params = CountryCodeAndPhoneNumberParams(TEST_COMPLETE_NUMBER, TEST_LANGUAGE)
        val phoneNumber = PhoneNumber(TEST_COUNTRY_CODE, TEST_PHONE_NUMBER, TEST_COUNTRY)
        `when`(countryCodeAndPhoneNumberUseCase.run(params)).thenReturn(Either.Right(phoneNumber))

        settingsAccountPhoneNumberViewModel.loadPhoneNumberData(TEST_COMPLETE_NUMBER, TEST_LANGUAGE)

        settingsAccountPhoneNumberViewModel.phoneNumberDetailsLiveData.awaitValue().let {
            it.number shouldBe TEST_PHONE_NUMBER
            it.countryCode shouldBe TEST_COUNTRY_CODE
            it.country shouldBe TEST_COUNTRY
        }
    }

    @Test
    fun `given country code is updated, then update confirmation data`() = runBlocking {
        val country = Country(TEST_COUNTRY, TEST_COUNTRY, TEST_COUNTRY_CODE)
        settingsAccountPhoneNumberViewModel.onCountryCodeUpdated(country)

        settingsAccountPhoneNumberViewModel.phoneNumberDetailsLiveData.awaitValue().let {
            it.number shouldBe String.empty()
            it.countryCode shouldBe TEST_COUNTRY_CODE
            it.country shouldBe TEST_COUNTRY
        }
    }

    @Test
    fun `given delete number clicked, when country code fails validation, then update country code error`() = runBlocking {
        val params = ValidatePhoneNumberParams(TEST_COUNTRY_CODE, TEST_PHONE_NUMBER)
        `when`(validatePhoneNumberUseCase.run(params)).thenReturn(Either.Left(CountryCodeInvalid))

        settingsAccountPhoneNumberViewModel.onDeleteNumberButtonClicked(TEST_COUNTRY_CODE, TEST_PHONE_NUMBER)

        settingsAccountPhoneNumberViewModel.countryCodeErrorLiveData.awaitValue().let {
            assertEquals(it.errorMessage, R.string.edit_phone_dialog_country_code_error)
        }
    }

    @Test
    fun `given delete number clicked, when number fails validation, then update phone number error`() = runBlocking {
        val params = ValidatePhoneNumberParams(TEST_COUNTRY_CODE, TEST_PHONE_NUMBER)
        `when`(validatePhoneNumberUseCase.run(params)).thenReturn(Either.Left(PhoneNumberInvalid))

        settingsAccountPhoneNumberViewModel.onDeleteNumberButtonClicked(TEST_COUNTRY_CODE, TEST_PHONE_NUMBER)

        settingsAccountPhoneNumberViewModel.phoneNumberErrorLiveData.awaitValue().let {
            assertEquals(it.errorMessage, R.string.edit_phone_dialog_phone_number_error)
        }
    }

    @Test
    fun `given delete number clicked, when both are valid, then update confirmation data`() = runBlocking {
        val params = ValidatePhoneNumberParams(TEST_COUNTRY_CODE, TEST_PHONE_NUMBER)
        `when`(validatePhoneNumberUseCase.run(params)).thenReturn(Either.Right(TEST_COMPLETE_NUMBER))

        settingsAccountPhoneNumberViewModel.onDeleteNumberButtonClicked(TEST_COUNTRY_CODE, TEST_PHONE_NUMBER)

        settingsAccountPhoneNumberViewModel.deleteNumberLiveData.awaitValue().let {
            it shouldBe TEST_COMPLETE_NUMBER
        }
    }

    @Test
    fun `given delete number confirmed, when deletion fails, then update deletion error`() = runBlocking {
        `when`(deletePhoneNumberUseCase.run(Unit)).thenReturn(Either.Left(CountryCodeInvalid))

        settingsAccountPhoneNumberViewModel.onDeleteNumberButtonConfirmed()

        settingsAccountPhoneNumberViewModel.phoneNumberErrorLiveData.awaitValue().let {
            assertEquals(it.errorMessage, R.string.pref__account_action__dialog__delete_phone__error)
        }
    }

    @Test
    fun `given phone number update confirmed, when update succeeds, then update confirmed data`() = runBlocking {
        val params = ChangePhoneNumberParams(TEST_COMPLETE_NUMBER)
        `when`(changePhoneNumberUseCase.run(params)).thenReturn(Either.Right(Unit))

        settingsAccountPhoneNumberViewModel.onPhoneNumberConfirmed(TEST_COMPLETE_NUMBER)

        settingsAccountPhoneNumberViewModel.confirmedLiveData.awaitValue().let {
            it shouldBe TEST_COMPLETE_NUMBER
        }
    }

    companion object {
        private const val TEST_COUNTRY = "Germany"
        private const val TEST_LANGUAGE = "en-gb"
        private const val TEST_COUNTRY_CODE = "+49"
        private const val TEST_PHONE_NUMBER = "88844477744"
        private const val TEST_COMPLETE_NUMBER = "${TEST_COUNTRY_CODE}${TEST_PHONE_NUMBER}"
    }

}
