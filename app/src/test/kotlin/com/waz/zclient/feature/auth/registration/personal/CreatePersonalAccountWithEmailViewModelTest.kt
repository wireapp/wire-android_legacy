package com.waz.zclient.feature.auth.registration.personal

import com.waz.zclient.R
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.config.PasswordLengthConfig
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.auth.registration.personal.email.CreatePersonalAccountWithEmailViewModel
import com.waz.zclient.feature.auth.registration.register.usecase.InvalidActivationCode
import com.waz.zclient.feature.auth.registration.register.usecase.RegisterPersonalAccountWithEmailUseCase
import com.waz.zclient.feature.auth.registration.register.usecase.UnauthorizedEmail
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.shared.activation.usecase.ActivateEmailParams
import com.waz.zclient.shared.activation.usecase.ActivateEmailUseCase
import com.waz.zclient.shared.activation.usecase.EmailBlacklisted
import com.waz.zclient.shared.activation.usecase.EmailInUse
import com.waz.zclient.shared.activation.usecase.InvalidCode
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeUseCase
import com.waz.zclient.shared.user.email.EmailInvalid
import com.waz.zclient.shared.user.email.EmailTooShort
import com.waz.zclient.shared.user.email.ValidateEmailUseCase
import com.waz.zclient.shared.user.name.ValidateNameUseCase
import com.waz.zclient.shared.user.password.ValidatePasswordUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountWithEmailViewModelTest : UnitTest() {

    @get:Rule
    val coroutinesTestRule = CoroutinesTestRule()

    private lateinit var createPersonalAccountWithEmailViewModel: CreatePersonalAccountWithEmailViewModel

    @Mock
    private lateinit var validateEmailUseCase: ValidateEmailUseCase

    @Mock
    private lateinit var sendEmailActivationCodeUseCase: SendEmailActivationCodeUseCase

    @Mock
    private lateinit var activateEmailUseCase: ActivateEmailUseCase

    @Mock
    private lateinit var validateNameUseCase: ValidateNameUseCase

    @Mock
    private lateinit var validatePasswordUseCase: ValidatePasswordUseCase

    @Mock
    private lateinit var passwordLengthConfig: PasswordLengthConfig

    @Mock
    private lateinit var registerPersonalAccountWithEmailUseCase: RegisterPersonalAccountWithEmailUseCase

    @Before
    fun setup() {
        createPersonalAccountWithEmailViewModel = CreatePersonalAccountWithEmailViewModel(
            validateEmailUseCase,
            sendEmailActivationCodeUseCase,
            activateEmailUseCase,
            validateNameUseCase,
            validatePasswordUseCase,
            passwordLengthConfig,
            registerPersonalAccountWithEmailUseCase
        )
    }

    @Test
    fun `given validateEmail is called, when the validation succeeds then ok button should be enabled`() =
        runBlockingTest {
            `when`(validateEmailUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountWithEmailViewModel.validateEmail(TEST_EMAIL)

            createPersonalAccountWithEmailViewModel.isValidEmailLiveData.observeOnce {
                assertTrue(it)
            }
        }

    @Test
    fun `given validateEmail is called, when the validation fails with EmailTooShortError then ok button should be disabled`() =
        runBlockingTest {
            `when`(validateEmailUseCase.run(any())).thenReturn(Either.Left(EmailTooShort))

            createPersonalAccountWithEmailViewModel.validateEmail(TEST_EMAIL)

            createPersonalAccountWithEmailViewModel.isValidEmailLiveData.observeOnce {
                assertFalse(it)
            }
        }

    @Test
    fun `given validateEmail is called, when the validation fails with EmailInvalidError then ok button should be disabled`() =
        runBlockingTest {
            `when`(validateEmailUseCase.run(any())).thenReturn(Either.Left(EmailInvalid))

            createPersonalAccountWithEmailViewModel.validateEmail(TEST_EMAIL)

            createPersonalAccountWithEmailViewModel.isValidEmailLiveData.observeOnce {
                assertFalse(it)
            }
        }

    @Test
    fun `given sendActivationCode is called, when the email is blacklisted then the activation code is not sent`() =
        runBlockingTest {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailBlacklisted))

            createPersonalAccountWithEmailViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountWithEmailViewModel.sendActivationCodeErrorLiveData.observeOnce {
                assertEquals(it.errorMessage, R.string.create_personal_account_with_email_email_blacklisted_error)
            }
        }

    @Test
    fun `given sendActivationCode is called, when the email is in use then the activation code is not sent`() =
        runBlockingTest {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailInUse))

            createPersonalAccountWithEmailViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountWithEmailViewModel.sendActivationCodeErrorLiveData.observeOnce {
                assertEquals(it.errorMessage, R.string.create_personal_account_with_email_email_in_use_error)
            }
        }

    @Test
    fun `given sendActivationCode is called, when there is no error then the activation code is sent`() =
        runBlockingTest {
            `when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountWithEmailViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountWithEmailViewModel.sendActivationCodeSuccessLiveData.observeOnce {
                assertEquals(it, Unit)
            }
        }

    @Test
    fun `given activateEmail is called, when the code is invalid then the activation is not done`() =
        runBlockingTest {
            val params = ActivateEmailParams(TEST_EMAIL, TEST_CODE)
            `when`(activateEmailUseCase.run(params)).thenReturn(Either.Left(InvalidCode))

            createPersonalAccountWithEmailViewModel.activateEmail(TEST_EMAIL, TEST_CODE)

            createPersonalAccountWithEmailViewModel.activateEmailErrorLiveData.observeOnce {
                assertEquals(it.errorMessage, R.string.email_verification_invalid_code_error)
            }
        }

    @Test
    fun `given activateEmail is called, when the code is valid then the activation is done`() =
        runBlockingTest {
            `when`(activateEmailUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountWithEmailViewModel.activateEmail(TEST_EMAIL, TEST_CODE)

            createPersonalAccountWithEmailViewModel.activateEmailSuccessLiveData.observeOnce {
                assertEquals(it, Unit)
            }
        }

    //TODO add missing tests for validateName()/validatePassword() once we agree on valid test solution for false positives

    @Test
    fun `given register is called, when the email is unauthorized then the registration is not done`() =
        runBlockingTest {
            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(UnauthorizedEmail))

            createPersonalAccountWithEmailViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            createPersonalAccountWithEmailViewModel.registerErrorLiveData.observeOnce {
                assertEquals(it.errorMessage, R.string.create_personal_account_unauthorized_email_error)
            }
        }

    @Test
    fun `given register is called, when the activation code is invalid then the registration is not done`() =
        runBlockingTest {
            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(InvalidActivationCode))

            createPersonalAccountWithEmailViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            createPersonalAccountWithEmailViewModel.registerErrorLiveData.observeOnce {
                assertEquals(it.errorMessage, R.string.create_personal_account_invalid_activation_code_error)
            }
        }

    @Test
    fun `given register is called, when the email is in use then the registration is not done`() =
        runBlockingTest {
            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(EmailInUse))

            createPersonalAccountWithEmailViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            createPersonalAccountWithEmailViewModel.registerErrorLiveData.observeOnce {
                assertEquals(it.errorMessage, R.string.create_personal_account_email_in_use_error)
            }
        }

    @Test
    fun `given register is called, when there is no error then the registration is done`() =
        runBlockingTest {
            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountWithEmailViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            createPersonalAccountWithEmailViewModel.registerSuccessLiveData.observeOnce {
                assertEquals(it, Unit)
            }
        }

    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_PASSWORD = "testPass"
        private const val TEST_CODE = "000000"
    }
}
