package com.waz.zclient.feature.auth.registration.personal

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.functional.Either
import com.waz.zclient.shared.activation.usecase.EmailBlackListed
import com.waz.zclient.shared.activation.usecase.EmailInUse
import com.waz.zclient.shared.activation.usecase.InvalidEmail
import com.waz.zclient.shared.activation.usecase.SendEmailActivationCodeUseCase
import com.waz.zclient.feature.auth.registration.personal.email.CreatePersonalAccountWithEmailViewModel
import com.waz.zclient.framework.livedata.observeOnce
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

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class CreatePersonalAccountWithEmailViewModelTest : UnitTest() {

    private lateinit var createPersonalAccountWithEmailViewModel: CreatePersonalAccountWithEmailViewModel

    @Mock
    private lateinit var validateEmailUseCase: ValidateEmailUseCase

    @Mock
    private lateinit var sendEmailActivationCodeUseCase: SendEmailActivationCodeUseCase

    @Before
    fun setup() {
        createPersonalAccountWithEmailViewModel = CreatePersonalAccountWithEmailViewModel(
            validateEmailUseCase, sendEmailActivationCodeUseCase)
    }

    @Test
    fun `given validateEmail is called, when the validation succeeds then ok button should be enabled`() =
        runBlockingTest {
            lenient().`when`(validateEmailUseCase.run(any())).thenReturn(Either.Right(TEST_EMAIL))

            createPersonalAccountWithEmailViewModel.validateEmail(TEST_EMAIL)

            createPersonalAccountWithEmailViewModel.confirmationButtonEnabledLiveData.observeOnce {
                it shouldBe true
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when the validation fails with EmailTooShortError then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(validateEmailUseCase.run(any())).thenReturn(Either.Left(EmailTooShort))

            createPersonalAccountWithEmailViewModel.validateEmail(TEST_EMAIL)

            createPersonalAccountWithEmailViewModel.confirmationButtonEnabledLiveData.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when the validation fails with EmailInvalidError then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(validateEmailUseCase.run(any())).thenReturn(Either.Left(EmailInvalid))

            createPersonalAccountWithEmailViewModel.validateEmail(TEST_EMAIL)

            createPersonalAccountWithEmailViewModel.confirmationButtonEnabledLiveData.observeOnce {
                it shouldBe false
            }
        }


    @Test
    fun `given sendActivationCode is called, when the email is invalid then the activation code is not sent`() =
        runBlockingTest {
            lenient().`when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(InvalidEmail))

            createPersonalAccountWithEmailViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountWithEmailViewModel.sendActivationCodeSuccessLiveData.observeOnce {
                it shouldBe InvalidEmail
            }
        }

    @Test
    fun `given sendActivationCode is called, when the email is blacklisted then the activation code is not sent`() =
        runBlockingTest {
            lenient().`when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailBlackListed))

            createPersonalAccountWithEmailViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountWithEmailViewModel.sendActivationCodeErrorLiveData.observeOnce {
                it shouldBe EmailBlackListed
            }
        }

    @Test
    fun `given sendActivationCode is called, when the email is in use then the activation code is not sent`() =
        runBlockingTest {
            lenient().`when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Left(EmailInUse))

            createPersonalAccountWithEmailViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountWithEmailViewModel.sendActivationCodeErrorLiveData.observeOnce {
                it shouldBe EmailInUse
            }
        }

    @Test
    fun `given sendActivationCode is called, when there is no error then the activation code is sent`() =
        runBlockingTest {
            lenient().`when`(sendEmailActivationCodeUseCase.run(any())).thenReturn(Either.Right(Unit))

            createPersonalAccountWithEmailViewModel.sendActivationCode(TEST_EMAIL)

            createPersonalAccountWithEmailViewModel.sendActivationCodeSuccessLiveData.observeOnce {
                it shouldBe Unit
            }
        }


    companion object {
        private const val TEST_EMAIL = "test@wire.com"
    }
}
