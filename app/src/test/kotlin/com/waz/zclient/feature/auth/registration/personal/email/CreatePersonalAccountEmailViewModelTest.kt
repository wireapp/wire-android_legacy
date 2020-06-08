package com.waz.zclient.feature.auth.registration.personal.email

import com.waz.zclient.R
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.activation.usecase.EmailBlacklisted
import com.waz.zclient.shared.activation.usecase.EmailInUse
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeUseCase
import com.waz.zclient.shared.user.email.EmailInvalid
import com.waz.zclient.shared.user.email.EmailTooShort
import com.waz.zclient.shared.user.email.ValidateEmailUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountEmailViewModelTest : UnitTest() {

    private lateinit var emailViewModel: CreatePersonalAccountEmailViewModel

    @Mock
    private lateinit var validateEmailUseCase: ValidateEmailUseCase

    @Mock
    private lateinit var sendEmailActivationCodeUseCase: SendEmailActivationCodeUseCase


    @Before
    fun setup() {
        emailViewModel = CreatePersonalAccountEmailViewModel(
            validateEmailUseCase,
            sendEmailActivationCodeUseCase
        )
    }

    @Test
    fun `given validateEmail is called, when the validation succeeds then isValidEmail should be true`() =
        coroutinesTestRule.runBlockingTest {
            `when`(validateEmailUseCase.run(any())).thenReturn(Either.Right(Unit))

            emailViewModel.validateEmail(TEST_EMAIL)

            assertTrue(emailViewModel.isValidEmailLiveData.awaitValue())
        }

    @Test
    fun `given validateEmail is called, when the validation fails with EmailTooShort error then isValidEmail should be false`() =
        coroutinesTestRule.runBlockingTest {
            `when`(validateEmailUseCase.run(any())).thenReturn(Either.Left(EmailTooShort))

            emailViewModel.validateEmail(TEST_EMAIL)

            assertFalse(emailViewModel.isValidEmailLiveData.awaitValue())
        }

    @Test
    fun `given validateEmail is called, when the validation fails with EmailInvalid error then isValidEmail should be false`() =
        coroutinesTestRule.runBlockingTest {
            `when`(validateEmailUseCase.run(any())).thenReturn(Either.Left(EmailInvalid))

            emailViewModel.validateEmail(TEST_EMAIL)

            assertFalse(emailViewModel.isValidEmailLiveData.awaitValue())
        }

    @Test
    fun `given sendActivationCode is called, when the email is blacklisted then an error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailBlacklisted))

            emailViewModel.sendActivationCode(TEST_EMAIL)

            val error = emailViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_email_email_blacklisted_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when the email is in use then an error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailInUse))

            emailViewModel.sendActivationCode(TEST_EMAIL)

            val error = emailViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_email_email_in_use_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when there is a network connection error then a network error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            emailViewModel.sendActivationCode(TEST_EMAIL)

            assertEquals(Unit, emailViewModel.networkConnectionErrorLiveData.awaitValue())
        }

    @Test
    fun `given sendActivationCode is called, when there is no error then the activation code is sent`() =
        coroutinesTestRule.runBlockingTest {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Right(Unit))

            emailViewModel.sendActivationCode(TEST_EMAIL)

            assertEquals(Unit, emailViewModel.sendActivationCodeSuccessLiveData.awaitValue())
        }

    companion object {
        private const val TEST_EMAIL = "test@wire.com"
    }
}
