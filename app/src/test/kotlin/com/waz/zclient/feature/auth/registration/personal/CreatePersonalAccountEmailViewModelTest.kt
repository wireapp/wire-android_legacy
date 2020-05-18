package com.waz.zclient.feature.auth.registration.personal

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.auth.registration.personal.email.CreatePersonalAccountEmailViewModel
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.shared.activation.usecase.EmailBlacklisted
import com.waz.zclient.shared.activation.usecase.EmailInUse
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeUseCase
import com.waz.zclient.shared.user.email.EmailInvalid
import com.waz.zclient.shared.user.email.EmailTooShort
import com.waz.zclient.shared.user.email.ValidateEmailUseCase
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
class CreatePersonalAccountEmailViewModelTest : UnitTest() {

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
        runBlockingTest {
            lenient().`when`(validateEmailUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountEmailViewModel.validateEmail(TEST_EMAIL)

            createPersonalAccountEmailViewModel.isValidEmailLiveData.observeOnce {
                it shouldBe true
            }
        }

    @Test
    fun `given validateEmail is called, when the validation fails with EmailTooShortError then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(validateEmailUseCase.run(any())).thenReturn(Either.Left(EmailTooShort))

            createPersonalAccountEmailViewModel.validateEmail(TEST_EMAIL)

            createPersonalAccountEmailViewModel.isValidEmailLiveData.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given validateEmail is called, when the validation fails with EmailInvalidError then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(validateEmailUseCase.run(any())).thenReturn(Either.Left(EmailInvalid))

            createPersonalAccountEmailViewModel.validateEmail(TEST_EMAIL)

            createPersonalAccountEmailViewModel.isValidEmailLiveData.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given sendActivationCode is called, when the email is blacklisted then the activation code is not sent`() =
        runBlockingTest {
            lenient().`when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailBlacklisted))

            createPersonalAccountEmailViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountEmailViewModel.sendActivationCodeErrorLiveData.observeOnce {
                it shouldBe EmailBlacklisted
            }
        }

    @Test
    fun `given sendActivationCode is called, when the email is in use then the activation code is not sent`() =
        runBlockingTest {
            lenient().`when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailInUse))

            createPersonalAccountEmailViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountEmailViewModel.sendActivationCodeErrorLiveData.observeOnce {
                it shouldBe EmailInUse
            }
        }

    @Test
    fun `given sendActivationCode is called, when there is a network connection error then the activation code is not sent`() =
        runBlockingTest {
            lenient().`when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            createPersonalAccountEmailViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountEmailViewModel.networkConnectionErrorLiveData.observeOnce {
                it shouldBe Unit
            }

            createPersonalAccountEmailViewModel.sendActivationCodeSuccessLiveData.observeOnce {
                verifyNoInteractions(it)
            }
        }

    @Test
    fun `given sendActivationCode is called, when there is no error then the activation code is sent`() =
        runBlockingTest {
            lenient().`when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountEmailViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountEmailViewModel.sendActivationCodeSuccessLiveData.observeOnce {
                it shouldBe Unit
            }
        }

    companion object {
        private const val TEST_EMAIL = "test@wire.com"
    }
}
