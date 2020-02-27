package com.waz.zclient.auth.registration.personal

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.user.domain.usecase.email.EmailInvalid
import com.waz.zclient.user.domain.usecase.email.EmailTooShort
import com.waz.zclient.user.domain.usecase.email.ValidateEmailUseCase
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
class CreatePersonalAccountViewModelTest : UnitTest() {

    private lateinit var createPersonalAccountViewModel: CreatePersonalAccountViewModel

    @Mock
    private lateinit var validateEmailUseCase: ValidateEmailUseCase

    @Before
    fun setup() {
        createPersonalAccountViewModel = CreatePersonalAccountViewModel(validateEmailUseCase)
    }


    @Test
    fun `given validateEmail is called, when the validation succeeds then ok button should be enabled`() =
        runBlockingTest {
            lenient().`when`(validateEmailUseCase.run(any())).thenReturn(Either.Right(TEST_EMAIL))

            createPersonalAccountViewModel.validateEmail(TEST_EMAIL)

            createPersonalAccountViewModel.confirmationButtonEnabledLiveData.observeOnce {
                it shouldBe true
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when the validation fails with EmailTooShortError then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(validateEmailUseCase.run(any())).thenReturn(Either.Left(EmailTooShort))

            createPersonalAccountViewModel.validateEmail(TEST_EMAIL)

            createPersonalAccountViewModel.confirmationButtonEnabledLiveData.observeOnce {
                it shouldBe false
            }
        }

    @Test
    fun `given onOkButtonClicked is called, when the validation fails with EmailInvalidError then ok button should be disabled`() =
        runBlockingTest {
            lenient().`when`(validateEmailUseCase.run(any())).thenReturn(Either.Left(EmailInvalid))

            createPersonalAccountViewModel.validateEmail(TEST_EMAIL)

            createPersonalAccountViewModel.confirmationButtonEnabledLiveData.observeOnce {
                it shouldBe false
            }
        }


    companion object {
        private const val TEST_EMAIL = "test@wire.com"
    }
}
