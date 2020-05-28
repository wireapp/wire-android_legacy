package com.waz.zclient.feature.auth.registration.personal

import com.waz.zclient.R
import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.config.PasswordLengthConfig
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.auth.registration.personal.password.CreatePersonalAccountPasswordViewModel
import com.waz.zclient.feature.auth.registration.register.usecase.InvalidActivationCode
import com.waz.zclient.feature.auth.registration.register.usecase.RegisterPersonalAccountWithEmailUseCase
import com.waz.zclient.feature.auth.registration.register.usecase.UnauthorizedEmail
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.activation.usecase.EmailInUse
import com.waz.zclient.shared.user.password.NoDigit
import com.waz.zclient.shared.user.password.NoLowerCaseLetter
import com.waz.zclient.shared.user.password.NoSpecialCharacter
import com.waz.zclient.shared.user.password.NoUpperCaseLetter
import com.waz.zclient.shared.user.password.PasswordTooLong
import com.waz.zclient.shared.user.password.PasswordTooShort
import com.waz.zclient.shared.user.password.ValidatePasswordUseCase
import junit.framework.Assert.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountPasswordViewModelTest : UnitTest() {

    @get:Rule
    val coroutinesTestRule = CoroutinesTestRule()

    private lateinit var createPersonalAccountPasswordViewModel: CreatePersonalAccountPasswordViewModel

    @Mock
    private lateinit var validatePasswordUseCase: ValidatePasswordUseCase

    @Mock
    private lateinit var passwordLengthConfig: PasswordLengthConfig

    @Mock
    private lateinit var registerPersonalAccountWithEmailUseCase: RegisterPersonalAccountWithEmailUseCase

    @Before
    fun setup() {
        createPersonalAccountPasswordViewModel = CreatePersonalAccountPasswordViewModel(
            validatePasswordUseCase,
            passwordLengthConfig,
            registerPersonalAccountWithEmailUseCase
        )
    }

    @Test
    fun `given validatePassword is called, when the validation fails with isPasswordTooShort then ok button should be disabled`() =
        runBlocking {
            `when`(validatePasswordUseCase.run(any())).thenReturn(Either.Left(PasswordTooShort))

            createPersonalAccountPasswordViewModel.validatePassword(TEST_PASSWORD)

            Assert.assertFalse(createPersonalAccountPasswordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given validatePassword is called, when the validation fails with PasswordTooLong then ok button should be disabled`() =
        runBlocking {
            `when`(validatePasswordUseCase.run(any())).thenReturn(Either.Left(PasswordTooLong))

            createPersonalAccountPasswordViewModel.validatePassword(TEST_PASSWORD)

            Assert.assertFalse(createPersonalAccountPasswordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given validatePassword is called, when the validation fails with NoLowerCaseLetter then ok button should be disabled`() =
        runBlocking {
            `when`(validatePasswordUseCase.run(any())).thenReturn(Either.Left(NoLowerCaseLetter))

            createPersonalAccountPasswordViewModel.validatePassword(TEST_PASSWORD)

            Assert.assertFalse(createPersonalAccountPasswordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given validatePassword is called, when the validation fails with NoUpperCaseLetter then ok button should be disabled`() =
        runBlocking {
            `when`(validatePasswordUseCase.run(any())).thenReturn(Either.Left(NoUpperCaseLetter))

            createPersonalAccountPasswordViewModel.validatePassword(TEST_PASSWORD)

            Assert.assertFalse(createPersonalAccountPasswordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given validatePassword is called, when the validation fails with NoDigit then ok button should be disabled`() =
        runBlocking {
            `when`(validatePasswordUseCase.run(any())).thenReturn(Either.Left(NoDigit))

            createPersonalAccountPasswordViewModel.validatePassword(TEST_PASSWORD)

            Assert.assertFalse(createPersonalAccountPasswordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given validatePassword is called, when the validation fails with NoSpecialCharacter then ok button should be disabled`() =
        runBlocking {
            `when`(validatePasswordUseCase.run(any())).thenReturn(Either.Left(NoSpecialCharacter))

            createPersonalAccountPasswordViewModel.validatePassword(TEST_PASSWORD)

            Assert.assertFalse(createPersonalAccountPasswordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given validatePassword is called, when the validation succeeds then ok button should be enabled`() =
        runBlocking {
            Mockito.`when`(validatePasswordUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountPasswordViewModel.validatePassword(TEST_PASSWORD)

            Assert.assertTrue(createPersonalAccountPasswordViewModel.isValidPasswordLiveData.awaitValue())
        }

    @Test
    fun `given register is called, when the email is unauthorized then the registration is not done`() =
        runBlocking {
            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(UnauthorizedEmail))

            createPersonalAccountPasswordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            val error = createPersonalAccountPasswordViewModel.registerErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_unauthorized_email_error, error.message)
        }

    @Test
    fun `given register is called, when the activation code is invalid then the registration is not done`() =
        runBlocking {
            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(InvalidActivationCode))

            createPersonalAccountPasswordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            val error = createPersonalAccountPasswordViewModel.registerErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_invalid_activation_code_error, error.message)
        }

    @Test
    fun `given register is called, when the email is in use then the registration is not done`() =
        runBlocking {
            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(EmailInUse))

            createPersonalAccountPasswordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            val error = createPersonalAccountPasswordViewModel.registerErrorLiveData.awaitValue()
            assertEquals(R.string.create_personal_account_email_in_use_error, error.message)
        }

    @Test
    fun `given register is called, when there is a network connection error then the registration is not done`() =
        runBlocking {

            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            createPersonalAccountPasswordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            assertEquals(Unit, createPersonalAccountPasswordViewModel.networkConnectionErrorLiveData.awaitValue())
        }

    @Test
    fun `given register is called, when there is no error then the registration is done`() =
        runBlocking {
            `when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountPasswordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            assertEquals(Unit, createPersonalAccountPasswordViewModel.registerSuccessLiveData.awaitValue())
        }

    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_PASSWORD = "testPass"
        private const val TEST_CODE = "000000"
    }
}
