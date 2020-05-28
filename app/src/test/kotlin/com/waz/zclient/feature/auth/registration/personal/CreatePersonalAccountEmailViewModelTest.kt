package com.waz.zclient.feature.auth.registration.personal

import com.waz.zclient.R
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.auth.registration.personal.email.CreatePersonalAccountEmailViewModel
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.activation.usecase.EmailBlacklisted
import com.waz.zclient.shared.activation.usecase.EmailInUse
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeUseCase
import com.waz.zclient.shared.user.email.EmailInvalid
import com.waz.zclient.shared.user.email.EmailTooShort
import com.waz.zclient.shared.user.email.ValidateEmailUseCase
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
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
class CreatePersonalAccountEmailViewModelTest : UnitTest() {

    @get:Rule
    val coroutinesTestRule = CoroutinesTestRule()

    private lateinit var createPersonalAccountEmailViewModel: CreatePersonalAccountEmailViewModel

    @Mock
    private lateinit var validateEmailUseCase: ValidateEmailUseCase

    @Mock
    private lateinit var sendEmailActivationCodeUseCase: SendEmailActivationCodeUseCase


    @Before
    fun setup() {
        createPersonalAccountEmailViewModel = CreatePersonalAccountEmailViewModel(
            validateEmailUseCase,
            sendEmailActivationCodeUseCase
        )
    }

    @Test
    fun `given validateEmail is called, when the validation succeeds then ok button should be enabled`() =
        runBlocking {
            `when`(validateEmailUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountEmailViewModel.validateEmail(TEST_EMAIL)

            assertTrue(createPersonalAccountEmailViewModel.isValidEmailLiveData.awaitValue())
        }

    @Test
    fun `given validateEmail is called, when the validation fails with EmailTooShortError then ok button should be disabled`() =
        runBlocking {
            `when`(validateEmailUseCase.run(any())).thenReturn(Either.Left(EmailTooShort))

            createPersonalAccountEmailViewModel.validateEmail(TEST_EMAIL)

            assertFalse(createPersonalAccountEmailViewModel.isValidEmailLiveData.awaitValue())
        }

    @Test
    fun `given validateEmail is called, when the validation fails with EmailInvalidError then ok button should be disabled`() =
        runBlocking {
            `when`(validateEmailUseCase.run(any())).thenReturn(Either.Left(EmailInvalid))

            createPersonalAccountEmailViewModel.validateEmail(TEST_EMAIL)

            assertFalse(createPersonalAccountEmailViewModel.isValidEmailLiveData.awaitValue())
        }

    @Test
    fun `given sendActivationCode is called, when the email is blacklisted then the activation code is not sent`() =
        runBlocking {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailBlacklisted))

            createPersonalAccountEmailViewModel.sendActivationCode(TEST_EMAIL)

            val error = createPersonalAccountEmailViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_email_email_blacklisted_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when the email is in use then the activation code is not sent`() =
        runBlocking {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailInUse))

            createPersonalAccountEmailViewModel.sendActivationCode(TEST_EMAIL)

            val error = createPersonalAccountEmailViewModel.sendActivationCodeErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_with_email_email_in_use_error, error.message)
        }

    @Test
    fun `given sendActivationCode is called, when there is a network connection error then the activation code is not sent`() =
        runBlocking {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            createPersonalAccountEmailViewModel.sendActivationCode(TEST_EMAIL)

            assertEquals(Unit, createPersonalAccountEmailViewModel.networkConnectionErrorLiveData.awaitValue())
        }

    @Test
    fun `given sendActivationCode is called, when there is no error then the activation code is sent`() =
        runBlocking {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountEmailViewModel.sendActivationCode(TEST_EMAIL)

            assertEquals(Unit, createPersonalAccountEmailViewModel.sendActivationCodeSuccessLiveData.awaitValue())
        }

    companion object {
        private const val TEST_EMAIL = "test@wire.com"
    }
}
