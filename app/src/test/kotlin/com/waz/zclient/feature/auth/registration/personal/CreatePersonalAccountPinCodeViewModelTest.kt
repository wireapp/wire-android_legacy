package com.waz.zclient.feature.auth.registration.personal

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.auth.registration.personal.pincode.CreatePersonalAccountPinCodeViewModel
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.shared.activation.usecase.ActivateEmailUseCase
import com.waz.zclient.shared.activation.usecase.EmailBlacklisted
import com.waz.zclient.shared.activation.usecase.EmailInUse
import com.waz.zclient.shared.activation.usecase.InvalidCode
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeUseCase
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
class CreatePersonalAccountPinCodeViewModelTest : UnitTest() {

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
        runBlockingTest {
            lenient().`when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailBlacklisted))

            createPersonalAccountPinCodeViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountPinCodeViewModel.sendActivationCodeErrorLiveData.observeOnce {
                it shouldBe EmailBlacklisted
            }
        }

    @Test
    fun `given sendActivationCode is called, when the email is in use then the activation code is not sent`() =
        runBlockingTest {
            lenient().`when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailInUse))

            createPersonalAccountPinCodeViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountPinCodeViewModel.sendActivationCodeErrorLiveData.observeOnce {
                it shouldBe EmailInUse
            }
        }

    @Test
    fun `given sendActivationCode is called, when there is a network connection error then the activation code is not sent`() =
        runBlockingTest {
            lenient().`when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            createPersonalAccountPinCodeViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountPinCodeViewModel.networkConnectionErrorLiveData.observeOnce {
                it shouldBe Unit
            }
            createPersonalAccountPinCodeViewModel.sendActivationCodeSuccessLiveData.observeOnce {
                verifyNoInteractions(it)
            }

        }

    @Test
    fun `given sendActivationCode is called, when there is no error then the activation code is sent`() =
        runBlockingTest {
            lenient().`when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountPinCodeViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountPinCodeViewModel.sendActivationCodeSuccessLiveData.observeOnce {
                it shouldBe Unit
            }
        }

    @Test
    fun `given activateEmail is called, when the code is invalid then the activation is not done`() =
        runBlockingTest {
            lenient().`when`(activateEmailUseCase.run(any())).thenReturn(Either.Left(InvalidCode))

            createPersonalAccountPinCodeViewModel.activateEmail(TEST_EMAIL, TEST_CODE)

            createPersonalAccountPinCodeViewModel.activateEmailErrorLiveData.observeOnce {
                it shouldBe InvalidCode
            }
        }

    @Test
    fun `given activateEmail is called, when there is a network connection error then the activation is not done`() =
        runBlockingTest {
            lenient().`when`(activateEmailUseCase.run(any())).thenReturn(Either.Left(NetworkConnection))

            createPersonalAccountPinCodeViewModel.activateEmail(TEST_EMAIL, TEST_CODE)

            createPersonalAccountPinCodeViewModel.networkConnectionErrorLiveData.observeOnce {
                it shouldBe Unit
            }
            createPersonalAccountPinCodeViewModel.activateEmailSuccessLiveData.observeOnce {
                verifyNoInteractions(it)
            }
        }

    @Test
    fun `given activateEmail is called, when the code is valid then the activation is done`() =
        runBlockingTest {
            lenient().`when`(activateEmailUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountPinCodeViewModel.activateEmail(TEST_EMAIL, TEST_CODE)

            createPersonalAccountPinCodeViewModel.activateEmailSuccessLiveData.observeOnce {
                it shouldBe Unit
            }
        }

    companion object {
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_CODE = "000000"
    }
}
