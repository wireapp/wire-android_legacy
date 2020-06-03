package com.waz.zclient.feature.auth.registration.personal.phone.code

import com.waz.zclient.R
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.activation.usecase.ActivatePhoneUseCase
import com.waz.zclient.shared.activation.usecase.InvalidPhoneCode
import com.waz.zclient.shared.activation.usecase.PhoneBlacklisted
import com.waz.zclient.shared.activation.usecase.PhoneInUse
import com.waz.zclient.shared.activation.usecase.SendPhoneActivationCodeUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountPhoneCodeViewModelTest : UnitTest() {

    @get:Rule
    val testRule = CoroutinesTestRule()

    private lateinit var phoneCodeViewModel: CreatePersonalAccountPhoneCodeViewModel

    @Mock
    private lateinit var sendPhoneActivationCodeUseCase: SendPhoneActivationCodeUseCase

    @Mock
    private lateinit var activatePhoneUseCase: ActivatePhoneUseCase


    @Before
    fun setup() {
        phoneCodeViewModel = CreatePersonalAccountPhoneCodeViewModel(
            sendPhoneActivationCodeUseCase,
            activatePhoneUseCase
        )
    }

    @Test
    fun `given sendActivationCode is called, when the phone is blacklisted then the activation code is not sent`() =
        runBlocking {
            `when`(sendPhoneActivationCodeUseCase.run(any())).thenReturn(Either.Left(PhoneBlacklisted))

            phoneCodeViewModel.sendActivationCode(TEST_PHONE)

            val error = phoneCodeViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_phone_phone_blacklisted_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when the phone is in use then the activation code is not sent`() =
        runBlocking {
            `when`(sendPhoneActivationCodeUseCase.run(any())).thenReturn(Either.Left(PhoneInUse))

            phoneCodeViewModel.sendActivationCode(TEST_PHONE)

            val error = phoneCodeViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_phone_phone_in_use_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when there is a network connection error then the activation code is not sent`() =
        runBlocking {
            `when`(sendPhoneActivationCodeUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            phoneCodeViewModel.sendActivationCode(TEST_PHONE)

            assertEquals(Unit, phoneCodeViewModel.networkConnectionErrorLiveData.awaitValue())
        }

    @Test
    fun `given sendActivationCode is called, when there is no error then the activation code is sent`() =
        runBlocking {
            `when`(sendPhoneActivationCodeUseCase.run(any())).thenReturn(Either.Right(Unit))

            phoneCodeViewModel.sendActivationCode(TEST_PHONE)

            assertEquals(Unit, phoneCodeViewModel.sendActivationCodeSuccessLiveData.awaitValue())
        }

    @Test
    fun `given activatePhone is called, when the code is invalid then the activation is not done`() =
        runBlocking {
            `when`(activatePhoneUseCase.run(any())).thenReturn(Either.Left(InvalidPhoneCode))

            phoneCodeViewModel.activatePhone(TEST_PHONE, TEST_CODE)

            val error = phoneCodeViewModel.activatePhoneErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_phone_code_invalid_code_error, error.message)
        }

    @Test
    fun `given activatePhone is called, when there is a network connection error then the activation is not done`() =
        runBlocking {
            `when`(activatePhoneUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            phoneCodeViewModel.activatePhone(TEST_PHONE, TEST_CODE)

            assertEquals(Unit, phoneCodeViewModel.networkConnectionErrorLiveData.awaitValue())
        }

    @Test
    fun `given activatePhone is called, when the code is valid then the activation is done`() =
        runBlocking {
            `when`(activatePhoneUseCase.run(any())).thenReturn(Either.Right(Unit))

            phoneCodeViewModel.activatePhone(TEST_PHONE, TEST_CODE)

            assertEquals(Unit, phoneCodeViewModel.activatePhoneSuccessLiveData.awaitValue())
        }

    companion object {
        private const val TEST_PHONE = "+499999999"
        private const val TEST_CODE = "000000"
    }
}
