package com.waz.zclient.feature.auth.registration.personal.phone

import com.waz.zclient.R
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.activation.usecase.PhoneBlacklisted
import com.waz.zclient.shared.activation.usecase.PhoneInUse
import com.waz.zclient.shared.activation.usecase.SendPhoneActivationCodeUseCase
import com.waz.zclient.shared.user.phonenumber.usecase.CountryCodeInvalid
import com.waz.zclient.shared.user.phonenumber.usecase.PhoneNumberInvalid
import com.waz.zclient.shared.user.phonenumber.usecase.ValidatePhoneNumberUseCase
import junit.framework.Assert.assertFalse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountPhoneViewModelTest : UnitTest() {

    private lateinit var phoneViewModel: CreatePersonalAccountPhoneViewModel

    @Mock
    private lateinit var validatePhoneNumberUseCase: ValidatePhoneNumberUseCase

    @Mock
    private lateinit var sendPhoneActivationCodeUseCase: SendPhoneActivationCodeUseCase


    @Before
    fun setup() {
        phoneViewModel = CreatePersonalAccountPhoneViewModel(
            validatePhoneNumberUseCase,
            sendPhoneActivationCodeUseCase
        )
    }

    @Test
    fun `given validatePhone is called, when the validation fails with CountryCodeInvalid then isValidPhone should be false`() =
        runBlocking {

            val countryCode = "+55555"
            val phoneNumber = "3099999999"

            `when`(validatePhoneNumberUseCase.run(any())).thenReturn(Either.Left(CountryCodeInvalid))

            phoneViewModel.validatePhone(countryCode, phoneNumber)

            assertFalse(phoneViewModel.isValidPhoneLiveData.awaitValue())
        }

    @Test
    fun `given validatePhone is called, when the validation fails with PhoneNumberInvalid then isValidPhone should be false`() =
        runBlocking {

            val countryCode = "+49"
            val phoneNumber = "30000000000000000000000"

            `when`(validatePhoneNumberUseCase.run(any())).thenReturn(Either.Left(PhoneNumberInvalid))

            phoneViewModel.validatePhone(countryCode, phoneNumber)

            assertFalse(phoneViewModel.isValidPhoneLiveData.awaitValue())
        }

    @Test
    fun `given validatePhone is called, when the validation succeeds then isValidPhone should be true`() =
        runBlocking {

            val countryCode = "+49"
            val phoneNumber = "3099999999"

            `when`(validatePhoneNumberUseCase.run(any())).thenReturn(Either.Right(countryCode.plus(phoneNumber)))

            phoneViewModel.validatePhone(countryCode, phoneNumber)

            assertTrue(phoneViewModel.isValidPhoneLiveData.awaitValue())
        }

    @Test
    fun `given sendActivationCode is called, when the phone is blacklisted then an error message is propagated`() =
        runBlocking {
            `when`(sendPhoneActivationCodeUseCase.run(any())).thenReturn(Either.Left(PhoneBlacklisted))

            phoneViewModel.sendActivationCode(TEST_PHONE)

            val error = phoneViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_phone_phone_blacklisted_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when the phone is in use then an error message is propagated`() =
        runBlocking {
            `when`(sendPhoneActivationCodeUseCase.run(any())).thenReturn(Either.Left(PhoneInUse))

            phoneViewModel.sendActivationCode(TEST_PHONE)

            val error = phoneViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_phone_phone_in_use_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when there is a network connection error then a network error message is propagated`() =
        runBlocking {
            `when`(sendPhoneActivationCodeUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            phoneViewModel.sendActivationCode(TEST_PHONE)

            assertEquals(Unit, phoneViewModel.networkConnectionErrorLiveData.awaitValue())
        }

    @Test
    fun `given sendActivationCode is called, when there is no error then the activation code is sent`() =
        runBlocking {
            `when`(sendPhoneActivationCodeUseCase.run(any())).thenReturn(Either.Right(Unit))

            phoneViewModel.sendActivationCode(TEST_PHONE)

            assertEquals(Unit, phoneViewModel.sendActivationCodeSuccessLiveData.awaitValue())
        }

    companion object {
        private const val TEST_PHONE = "+493099999999"
    }
}
