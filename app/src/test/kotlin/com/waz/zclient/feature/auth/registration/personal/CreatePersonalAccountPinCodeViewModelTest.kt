package com.waz.zclient.feature.auth.registration.personal

import com.waz.zclient.R
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.auth.registration.personal.pincode.CreatePersonalAccountPinCodeViewModel
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.activation.usecase.ActivateEmailUseCase
import com.waz.zclient.shared.activation.usecase.EmailBlacklisted
import com.waz.zclient.shared.activation.usecase.EmailInUse
import com.waz.zclient.shared.activation.usecase.InvalidCode
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeUseCase
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountPinCodeViewModelTest : UnitTest() {

    @get:Rule
    val coroutinesTestRule = CoroutinesTestRule()

    private lateinit var createPersonalAccountPinCodeViewModel: CreatePersonalAccountPinCodeViewModel

    @Mock
    private lateinit var sendEmailActivationCodeUseCase: SendEmailActivationCodeUseCase

    @Mock
    private lateinit var activateEmailUseCase: ActivateEmailUseCase


    @Before
    fun setup() {
        createPersonalAccountPinCodeViewModel = CreatePersonalAccountPinCodeViewModel(
            sendEmailActivationCodeUseCase,
            activateEmailUseCase
        )
    }

    @Test
    fun `given sendActivationCode is called, when the email is blacklisted then the activation code is not sent`() =
        runBlocking {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailBlacklisted))

            createPersonalAccountPinCodeViewModel.sendActivationCode(TEST_EMAIL)

            val error = createPersonalAccountPinCodeViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_email_email_blacklisted_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when the email is in use then the activation code is not sent`() =
        runBlocking {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailInUse))

            createPersonalAccountPinCodeViewModel.sendActivationCode(TEST_EMAIL)

            val error = createPersonalAccountPinCodeViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_email_email_in_use_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when there is a network connection error then the activation code is not sent`() =
        runBlocking {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            createPersonalAccountPinCodeViewModel.sendActivationCode(TEST_EMAIL)

            assertEquals(Unit, createPersonalAccountPinCodeViewModel.networkConnectionErrorLiveData.awaitValue())
        }

    @Test
    fun `given sendActivationCode is called, when there is no error then the activation code is sent`() =
        runBlocking {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountPinCodeViewModel.sendActivationCode(TEST_EMAIL)

            assertEquals(Unit, createPersonalAccountPinCodeViewModel.sendActivationCodeSuccessLiveData.awaitValue())
        }

    @Test
    fun `given activateEmail is called, when the code is invalid then the activation is not done`() =
        runBlocking {
            `when`(activateEmailUseCase.run(any())).thenReturn(Either.Left(InvalidCode))

            createPersonalAccountPinCodeViewModel.activateEmail(TEST_EMAIL, TEST_CODE)

            val error = createPersonalAccountPinCodeViewModel.activateEmailErrorLiveData.awaitValue()
            assertEquals(R.string.email_verification_invalid_code_error, error.message)
        }

    @Test
    fun `given activateEmail is called, when there is a network connection error then the activation is not done`() =
        runBlocking {
            `when`(activateEmailUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            createPersonalAccountPinCodeViewModel.activateEmail(TEST_EMAIL, TEST_CODE)

            assertEquals(Unit, createPersonalAccountPinCodeViewModel.networkConnectionErrorLiveData.awaitValue())
        }

    @Test
    fun `given activateEmail is called, when the code is valid then the activation is done`() =
        runBlocking {
            `when`(activateEmailUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountPinCodeViewModel.activateEmail(TEST_EMAIL, TEST_CODE)

            assertEquals(Unit, createPersonalAccountPinCodeViewModel.activateEmailSuccessLiveData.awaitValue())
        }

    companion object {
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_CODE = "000000"
    }
}
