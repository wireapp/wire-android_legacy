package com.waz.zclient.settings.account

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.HttpError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.framework.livedata.observeOnce
import com.waz.zclient.user.domain.model.User
import com.waz.zclient.user.domain.usecase.*
import com.waz.zclient.user.domain.usecase.handle.ChangeHandleParams
import com.waz.zclient.user.domain.usecase.handle.ChangeHandleUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock


@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class SettingsAccountViewModelTest : UnitTest() {

    companion object {
        private const val TEST_NAME = "testName"
        private const val TEST_HANDLE = "@Wire"
        private const val TEST_EMAIL = "email@wire.com"
        private const val TEST_PHONE = "+497573889375"
        private const val TEST_ERROR_CODE = 401
        private const val TEST_ERROR_MESSAGE = "Unauthorised Error"
    }

    private lateinit var viewModel: SettingsAccountViewModel

    @Mock
    private lateinit var getUserProfileUseCase: GetUserProfileUseCase

    @Mock
    private lateinit var changeNameUseCase: ChangeNameUseCase

    @Mock
    private lateinit var changePhoneUseCase: ChangePhoneUseCase

    @Mock
    private lateinit var changeEmailUseCase: ChangeEmailUseCase

    @Mock
    private lateinit var changeHandleUseCase: ChangeHandleUseCase

    @Mock
    private lateinit var user: User

    private lateinit var userFlow: Flow<User>

    @Before
    fun setup() {
        viewModel = SettingsAccountViewModel(
            getUserProfileUseCase,
            changeNameUseCase,
            changePhoneUseCase,
            changeEmailUseCase,
            changeHandleUseCase)
        userFlow = flow { user }
    }

    @Test
    fun `given profile is loaded successfully, then account name observer is notified`() = runBlockingTest {
        lenient().`when`(getUserProfileUseCase.run(Unit)).thenReturn(userFlow)
        lenient().`when`(user.name).thenReturn(TEST_NAME)

        viewModel.loadProfileDetails()

        userFlow.collect {
            viewModel.name.observeOnce {
                it shouldBe TEST_NAME
            }
        }
    }

    @Test
    fun `given profile is loaded successfully, then account handle observer is notified`() = runBlockingTest {
        lenient().`when`(getUserProfileUseCase.run(Unit)).thenReturn(userFlow)
        lenient().`when`(user.handle).thenReturn(TEST_HANDLE)

        viewModel.loadProfileDetails()

        userFlow.collect {
            viewModel.handle.observeOnce {
                it shouldBe TEST_HANDLE
            }
        }
    }

    @Test
    fun `given profile is loaded successfully and account email is not null, then account email observer is notified and user email state is success`() = runBlockingTest {
        lenient().`when`(getUserProfileUseCase.run(Unit)).thenReturn(userFlow)
        lenient().`when`(user.email).thenReturn(TEST_EMAIL)

        viewModel.loadProfileDetails()

        userFlow.collect {
            viewModel.email.observeOnce {
                it shouldBe ProfileDetail(TEST_NAME)
            }
        }
    }

    @Test
    fun `given profile is loaded successfully and account email is null, then account email observer is notified and then user email state isNull`() = runBlockingTest {
        lenient().`when`(getUserProfileUseCase.run(Unit)).thenReturn(userFlow)
        lenient().`when`(user.email).thenReturn(null)

        viewModel.loadProfileDetails()

        userFlow.collect {
            viewModel.email.observeOnce {
                it shouldBe ProfileDetail.EMPTY
            }
        }
    }

    @Test
    fun `given profile is loaded successfully and account phone is not null, then account phone observer is notified and then user phone state is success`() = runBlockingTest {
        lenient().`when`(getUserProfileUseCase.run(Unit)).thenReturn(userFlow)
        lenient().`when`(user.email).thenReturn(TEST_PHONE)

        viewModel.loadProfileDetails()

        userFlow.collect {
            viewModel.phone.observeOnce {
                it shouldBe ProfileDetail(TEST_PHONE)
            }
        }
    }

    @Test
    fun `given profile is loaded successfully and account phone is null, then account phone observer is notified and then user phone state isNull`() = runBlockingTest {
        lenient().`when`(getUserProfileUseCase.run(Unit)).thenReturn(userFlow)
        lenient().`when`(user.email).thenReturn(null)

        viewModel.loadProfileDetails()

        userFlow.collect {
            viewModel.phone.observeOnce {
                it shouldBe ProfileDetail.EMPTY
            }
        }
    }

    @Test
    fun `given account name is updated and fails with HttpError, then error observer is notified`() {
        val changeNameParams = mock(ChangeNameParams::class.java)

        runBlockingTest { lenient().`when`(changeNameUseCase.run(changeNameParams)).thenReturn(Either.Left(HttpError(TEST_ERROR_CODE, TEST_ERROR_MESSAGE))) }

        viewModel.updateName(TEST_NAME)

        viewModel.error.observeOnce {
            it shouldBe "$TEST_ERROR_CODE + $TEST_ERROR_MESSAGE"
        }

    }

    @Test
    fun `given account handle is updated and fails with HttpError, then error observer is notified`() {
        val changeHandleParams = mock(ChangeHandleParams::class.java)

        runBlockingTest { lenient().`when`(changeHandleUseCase.run(changeHandleParams)).thenReturn(Either.Left(HttpError(TEST_ERROR_CODE, TEST_ERROR_MESSAGE))) }

        viewModel.updateHandle(TEST_HANDLE)

        viewModel.error.observeOnce {
            it shouldBe "$TEST_ERROR_CODE + $TEST_ERROR_MESSAGE"
        }
    }

    @Test
    fun `given account email is updated and fails with HttpError, then error observer is notified`() {
        val changeEmailParams = mock(ChangeEmailParams::class.java)

        runBlockingTest { lenient().`when`(changeEmailUseCase.run(changeEmailParams)).thenReturn(Either.Left(HttpError(TEST_ERROR_CODE, TEST_ERROR_MESSAGE))) }

        viewModel.updateEmail(TEST_EMAIL)

        viewModel.error.observeOnce {
            it shouldBe "$TEST_ERROR_CODE + $TEST_ERROR_MESSAGE"
        }
    }

    @Test
    fun `given account phone is updated and fails with HttpError, then error observer is notified`() {
        val changePhoneParams = mock(ChangePhoneParams::class.java)

        runBlockingTest { lenient().`when`(changePhoneUseCase.run(changePhoneParams)).thenReturn(Either.Left(HttpError(TEST_ERROR_CODE, TEST_ERROR_MESSAGE))) }

        viewModel.updatePhone(TEST_PHONE)

        viewModel.error.observeOnce {
            it shouldBe "$TEST_ERROR_CODE + $TEST_ERROR_MESSAGE"
        }
    }
}
