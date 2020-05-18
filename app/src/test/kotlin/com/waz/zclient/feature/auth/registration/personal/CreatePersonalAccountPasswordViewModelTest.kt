package com.waz.zclient.feature.auth.registration.personal

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.config.PasswordLengthConfig
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.auth.registration.personal.password.CreatePersonalAccountPasswordViewModel
import com.waz.zclient.feature.auth.registration.register.usecase.InvalidActivationCode
import com.waz.zclient.feature.auth.registration.register.usecase.RegisterPersonalAccountWithEmailUseCase
import com.waz.zclient.feature.auth.registration.register.usecase.UnauthorizedEmail
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.shared.activation.usecase.EmailInUse
import com.waz.zclient.shared.user.password.ValidatePasswordUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verifyNoInteractions

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountPasswordViewModelTest : UnitTest() {

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
    //TODO add missing tests for validatePassword() once the solution for false positives is merged

    @Test
    fun `given register is called, when the email is unauthorized then the registration is not done`() =
        runBlockingTest {
            lenient().`when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(UnauthorizedEmail))

            createPersonalAccountPasswordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            createPersonalAccountPasswordViewModel.registerErrorLiveData.observeOnce {
                it shouldBe UnauthorizedEmail
            }
        }

    @Test
    fun `given register is called, when the activation code is invalid then the registration is not done`() =
        runBlockingTest {
            lenient().`when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(InvalidActivationCode))

            createPersonalAccountPasswordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            createPersonalAccountPasswordViewModel.registerErrorLiveData.observeOnce {
                it shouldBe InvalidActivationCode
            }
        }

    @Test
    fun `given register is called, when the email is in use then the registration is not done`() =
        runBlockingTest {
            lenient().`when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(EmailInUse))

            createPersonalAccountPasswordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            createPersonalAccountPasswordViewModel.registerErrorLiveData.observeOnce {
                it shouldBe EmailInUse
            }
        }

    @Test
    fun `given register is called, when there is a network connection error then the registration is not done`() =
        runBlockingTest {

            lenient().`when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            createPersonalAccountPasswordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            createPersonalAccountPasswordViewModel.networkConnectionErrorLiveData.observeOnce {
                it shouldBe Unit
            }

            createPersonalAccountPasswordViewModel.registerSuccessLiveData.observeOnce {
                verifyNoInteractions(it)
            }
        }

    @Test
    fun `given register is called, when there is no error then the registration is done`() =
        runBlockingTest {
            lenient().`when`(registerPersonalAccountWithEmailUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountPasswordViewModel.register(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_CODE)

            createPersonalAccountPasswordViewModel.registerSuccessLiveData.observeOnce {
                it shouldBe Unit
            }
        }

    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_PASSWORD = "testPass"
        private const val TEST_CODE = "000000"
    }
}
