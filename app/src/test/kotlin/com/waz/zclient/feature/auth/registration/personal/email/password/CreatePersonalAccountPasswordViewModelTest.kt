package com.waz.zclient.feature.auth.registration.personal.email.password

import com.waz.zclient.R
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.config.PasswordLengthConfig
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.auth.registration.register.usecase.EmailInUse
import com.waz.zclient.feature.auth.registration.register.usecase.InvalidEmailActivationCode
import com.waz.zclient.feature.auth.registration.register.usecase.RegisterPersonalAccountWithEmailUseCase
import com.waz.zclient.feature.auth.registration.register.usecase.UnauthorizedEmail
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.user.password.NoDigit
import com.waz.zclient.shared.user.password.NoLowerCaseLetter
import com.waz.zclient.shared.user.password.NoSpecialCharacter
import com.waz.zclient.shared.user.password.NoUpperCaseLetter
import com.waz.zclient.shared.user.password.PasswordTooLong
import com.waz.zclient.shared.user.password.PasswordTooShort
import com.waz.zclient.shared.user.password.ValidatePasswordUseCase
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountPasswordViewModelTest : UnitTest() {

    private lateinit var passwordViewModel: CreatePersonalAccountPasswordViewModel

    @Mock
    private lateinit var validatePasswordUseCase: ValidatePasswordUseCase

    @Mock
    private lateinit var passwordLengthConfig: PasswordLengthConfig

    @Mock
    private lateinit var registerPersonalAccountWithEmailUseCase: RegisterPersonalAccountWithEmailUseCase

    @Before
    fun setup() {
        passwordViewModel = CreatePersonalAccountPasswordViewModel(
            validatePasswordUseCase,
            passwordLengthConfig,
            registerPersonalAccountWithEmailUseCase
        )
    }

    @Test
    fun `given validatePassword is called, when the validation fails with isPasswordTooShort then isValidPassword should be false`() =
        coroutinesTestRule.runBlockingTest {
            `when`(validatePasswordUseCase.run(any())).thenReturn(Either.Left(PasswordTooShort))

            passwordViewModel.validatePassword(TEST_PASSWORD)

            assertFalse(passwordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given validatePassword is called, when the validation fails with PasswordTooLong then isValidPassword should be false`() =
        coroutinesTestRule.runBlockingTest {
            `when`(validatePasswordUseCase.run(any())).thenReturn(Either.Left(PasswordTooLong))

            passwordViewModel.validatePassword(TEST_PASSWORD)

            assertFalse(passwordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given validatePassword is called, when the validation fails with NoLowerCaseLetter then isValidPassword should be false`() =
        coroutinesTestRule.runBlockingTest {
            `when`(validatePasswordUseCase.run(any())).thenReturn(Either.Left(NoLowerCaseLetter))

            passwordViewModel.validatePassword(TEST_PASSWORD)

            assertFalse(passwordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given validatePassword is called, when the validation fails with NoUpperCaseLetter then isValidPassword should be false`() =
        coroutinesTestRule.runBlockingTest {
            `when`(validatePasswordUseCase.run(any())).thenReturn(Either.Left(NoUpperCaseLetter))

            passwordViewModel.validatePassword(TEST_PASSWORD)

            assertFalse(passwordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given validatePassword is called, when the validation fails with NoDigit then isValidPassword should be false`() =
        coroutinesTestRule.runBlockingTest {
            `when`(validatePasswordUseCase.run(any())).thenReturn(Either.Left(NoDigit))

            passwordViewModel.validatePassword(TEST_PASSWORD)

            assertFalse(passwordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given validatePassword is called, when the validation fails with NoSpecialCharacter then isValidPassword should be false`() =
        coroutinesTestRule.runBlockingTest {
            `when`(validatePasswordUseCase.run(any())).thenReturn(Either.Left(NoSpecialCharacter))

            passwordViewModel.validatePassword(TEST_PASSWORD)

            assertFalse(passwordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given validatePassword is called, when the validation succeeds then isValidPassword should be true`() =
        coroutinesTestRule.runBlockingTest {
            `when`(validatePasswordUseCase.run(any())).thenReturn(Either.Right(Unit))

            passwordViewModel.validatePassword(TEST_PASSWORD)

            assertTrue(passwordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given register is called, when the email is unauthorized then an error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(UnauthorizedEmail))

            passwordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            val error = passwordViewModel.registerErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_unauthorized_email_error, error.message)
        }

    @Test
    fun `given register is called, when the activation code is invalid then an error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(InvalidEmailActivationCode))

            passwordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            val error = passwordViewModel.registerErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_invalid_activation_code_error, error.message)
        }

    @Test
    fun `given register is called, when the email is in use then an error message is propagated`() =
        coroutinesTestRule.runBlockingTest {
            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(EmailInUse))

            passwordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            val error = passwordViewModel.registerErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_email_in_use_error, error.message)
        }

    @Test
    fun `given register is called, when there is a network connection error then a network error message is propagated`() =
        coroutinesTestRule.runBlockingTest {

            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            passwordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            assertEquals(Unit, passwordViewModel.networkConnectionErrorLiveData.awaitValue())
        }

    @Test
    fun `given register is called, when there is no error then the registration is done`() =
        coroutinesTestRule.runBlockingTest {
            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Right(Unit))

            passwordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            assertEquals(Unit, passwordViewModel.registerSuccessLiveData.awaitValue())
        }

    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_PASSWORD = "testPass"
        private const val TEST_CODE = "000000"
    }
}
