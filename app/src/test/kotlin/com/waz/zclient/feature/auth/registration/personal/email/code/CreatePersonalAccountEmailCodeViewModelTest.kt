package com.waz.zclient.feature.auth.registration.personal.email.code

import com.waz.zclient.R
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.activation.usecase.ActivateEmailUseCase
import com.waz.zclient.shared.activation.usecase.EmailBlacklisted
import com.waz.zclient.shared.activation.usecase.EmailInUse
import com.waz.zclient.shared.activation.usecase.InvalidEmailCode
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountEmailCodeViewModelTest : UnitTest() {

    private lateinit var emailCodeViewModel: CreatePersonalAccountEmailCodeViewModel

    @Mock
    private lateinit var sendEmailActivationCodeUseCase: SendEmailActivationCodeUseCase

    @Mock
    private lateinit var activateEmailUseCase: ActivateEmailUseCase


    @Before
    fun setup() {
        emailCodeViewModel = CreatePersonalAccountEmailCodeViewModel(
            sendEmailActivationCodeUseCase,
            activateEmailUseCase
        )
    }

    @Test
    fun `given sendActivationCode is called, when the email is blacklisted then an error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailBlacklisted))

            emailCodeViewModel.sendActivationCode(TEST_EMAIL)

            val error = emailCodeViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_email_email_blacklisted_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when the email is in use then an error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailInUse))

            emailCodeViewModel.sendActivationCode(TEST_EMAIL)

            val error = emailCodeViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_email_email_in_use_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when there is a network connection error then a network error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            emailCodeViewModel.sendActivationCode(TEST_EMAIL)

            assertEquals(Unit, emailCodeViewModel.networkConnectionErrorLiveData.awaitValue())
        }

    @Test
    fun `given sendActivationCode is called, when there is no error then the activation code is sent`() =
        coroutinesTestRule.runBlockingTest {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Right(Unit))

            emailCodeViewModel.sendActivationCode(TEST_EMAIL)

            assertEquals(Unit, emailCodeViewModel.sendActivationCodeSuccessLiveData.awaitValue())
        }

    @Test
    fun `given activateEmail is called, when the code is invalid then an error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(activateEmailUseCase.run(any())).thenReturn(Either.Left(InvalidEmailCode))

            emailCodeViewModel.activateEmail(TEST_EMAIL, TEST_CODE)

            val error = emailCodeViewModel.activateEmailErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_email_code_invalid_code_error, error.message)
        }

    @Test
    fun `given activateEmail is called, when there is a network connection error then a network error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(activateEmailUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            emailCodeViewModel.activateEmail(TEST_EMAIL, TEST_CODE)

            assertEquals(Unit, emailCodeViewModel.networkConnectionErrorLiveData.awaitValue())
        }

    @Test
    fun `given activateEmail is called, when the code is valid then the activation is done`() =
        coroutinesTestRule.runBlockingTest {
            `when`(activateEmailUseCase.run(any())).thenReturn(Either.Right(Unit))

            emailCodeViewModel.activateEmail(TEST_EMAIL, TEST_CODE)

            assertEquals(Unit, emailCodeViewModel.activateEmailSuccessLiveData.awaitValue())
        }

    companion object {
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_CODE = "000000"
    }
}
