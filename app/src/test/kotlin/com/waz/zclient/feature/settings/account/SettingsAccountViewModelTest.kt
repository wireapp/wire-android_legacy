package com.waz.zclient.feature.settings.account

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.waz.zclient.UnitTest
import com.waz.zclient.core.config.AccountUrlConfig
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.feature.settings.account.logout.AnotherAccountExists
import com.waz.zclient.feature.settings.account.logout.CouldNotReadRemainingAccounts
import com.waz.zclient.feature.settings.account.logout.NoAccountsLeft
import com.waz.zclient.framework.coroutines.CoroutinesTestRule
import com.waz.zclient.framework.livedata.awaitValue
import com.waz.zclient.shared.accounts.usecase.GetActiveAccountUseCase
import com.waz.zclient.shared.user.User
import com.waz.zclient.shared.user.email.ChangeEmailParams
import com.waz.zclient.shared.user.email.ChangeEmailUseCase
import com.waz.zclient.shared.user.name.ChangeNameParams
import com.waz.zclient.shared.user.name.ChangeNameUseCase
import com.waz.zclient.shared.user.profile.GetUserProfileUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class SettingsAccountViewModelTest : UnitTest() {

    @get:Rule
    val coroutinesTestRule = CoroutinesTestRule()

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private lateinit var viewModel: SettingsAccountViewModel

    @Mock
    private lateinit var getUserProfileUseCase: GetUserProfileUseCase

    @Mock
    private lateinit var changeNameUseCase: ChangeNameUseCase

    @Mock
    private lateinit var changeEmailUseCase: ChangeEmailUseCase

    @Mock
    private lateinit var accountsUrlConfig: AccountUrlConfig

    @Mock
    private lateinit var getActiveAccountUseCase: GetActiveAccountUseCase

    @Mock
    private lateinit var user: User

    private lateinit var userFlow: Flow<User>

    @Before
    fun setup() {
        runBlocking {
            //TODO: add test cases for getActiveAccountUseCase
            `when`(getActiveAccountUseCase.run(Unit)).thenReturn(Either.Left(ServerError))
        }
        viewModel = SettingsAccountViewModel(
            getUserProfileUseCase,
            changeNameUseCase,
            changeEmailUseCase,
            getActiveAccountUseCase,
            accountsUrlConfig)
        userFlow = flowOf(user)
    }

    @Test
    fun `given profile is loaded successfully, then account name observer is notified`() {
        runBlocking {
            `when`(user.name).thenReturn(TEST_NAME)
            `when`(getUserProfileUseCase.run(Unit)).thenReturn(userFlow)

            viewModel.loadProfileDetails()

            assertEquals(TEST_NAME, viewModel.nameLiveData.awaitValue())
            verify(getUserProfileUseCase).run(Unit)
        }
    }

    @Test
    fun `given profile is loaded successfully, then account handle observer is notified`() {
        runBlocking {
            `when`(user.handle).thenReturn(TEST_HANDLE)
            `when`(getUserProfileUseCase.run(Unit)).thenReturn(userFlow)

            viewModel.loadProfileDetails()

            assertEquals(TEST_HANDLE, viewModel.handleLiveData.awaitValue())
            verify(getUserProfileUseCase).run(Unit)
        }
    }


    @Test
    fun `given profile is loaded successfully and account email is not null, then account email observer is notified and user email state is success`() {
        runBlocking {
            `when`(user.email).thenReturn(TEST_EMAIL)
            `when`(getUserProfileUseCase.run(Unit)).thenReturn(userFlow)

            viewModel.loadProfileDetails()

            assertEquals(ProfileDetail(TEST_EMAIL), viewModel.emailLiveData.awaitValue())
            verify(getUserProfileUseCase).run(Unit)
        }
    }

    @Test
    fun `given profile is loaded successfully and account email is null, then account email observer is notified and then user email state isNull`() {
        runBlocking {
            `when`(user.email).thenReturn(null)
            `when`(getUserProfileUseCase.run(Unit)).thenReturn(userFlow)

            viewModel.loadProfileDetails()

            assertEquals(ProfileDetail.EMPTY, viewModel.emailLiveData.awaitValue())
            verify(getUserProfileUseCase).run(Unit)
        }
    }

    @Test
    fun `given profile is loaded successfully and account phone is not null, then account phone observer is notified and then user phone state is success`() {
        runBlocking {
            `when`(user.phone).thenReturn(TEST_PHONE)
            `when`(getUserProfileUseCase.run(Unit)).thenReturn(userFlow)

            viewModel.loadProfileDetails()

            assertEquals(ProfileDetail(TEST_PHONE), viewModel.phoneNumberLiveData.awaitValue())
            verify(getUserProfileUseCase).run(Unit)
        }
    }

    @Test
    fun `given profile is loaded successfully and account phone is null, then account phone observer is notified and then user phone state isNull`() {
        runBlocking {
            `when`(user.phone).thenReturn(null)
            `when`(getUserProfileUseCase.run(Unit)).thenReturn(userFlow)

            viewModel.loadProfileDetails()

            assertEquals(ProfileDetail.EMPTY, viewModel.phoneNumberLiveData.awaitValue())
            verify(getUserProfileUseCase).run(Unit)
        }
    }

    @Test
    fun `given account name is updated and fails with HttpError, then error observer is notified`() {
        val changeNameParams = ChangeNameParams(TEST_NAME)

        runBlocking {
            `when`(changeNameUseCase.run(changeNameParams)).thenReturn(Either.Left(ServerError))

            viewModel.updateName(TEST_NAME)

            assertEquals("Failure: $ServerError", viewModel.errorLiveData.awaitValue())
        }
    }

    @Test
    fun `given account email is updated and fails with HttpError, then error observer is notified`() {
        val changeEmailParams = ChangeEmailParams(TEST_EMAIL)

        runBlocking {
            `when`(changeEmailUseCase.run(changeEmailParams)).thenReturn(Either.Left(ServerError))

            viewModel.updateEmail(TEST_EMAIL)

            assertEquals("Failure: $ServerError", viewModel.errorLiveData.awaitValue())
        }
    }

    @Test
    fun `given reset password is clicked, then url observer is notified`() {
        `when`(accountsUrlConfig.url).thenReturn(TEST_ACCOUNT_CONFIG_URL)

        viewModel.onResetPasswordClicked()

        runBlocking {
            val url = viewModel.resetPasswordUrlLiveData.awaitValue()
            assertEquals("$TEST_ACCOUNT_CONFIG_URL$TEST_RESET_PASSWORD_URL_SUFFIX", url)
        }
    }

    @Test
    fun `given onUserLoggedOut called with status NoAccountsLeft, then updates logoutNavigationAction with ACTION_NO_USER_LEFT`() {
        viewModel.onUserLoggedOut(NoAccountsLeft)

        runBlocking {
            assertEquals("com.wire.ACTION_NO_USER_LEFT", viewModel.logoutNavigationAction.awaitValue())
        }
    }

    @Test
    fun `given onUserLoggedOut called with status CouldNotReadRemainingAccounts, then updates logoutNavigationAction with ACTION_NO_USER_LEFT`() {
        viewModel.onUserLoggedOut(CouldNotReadRemainingAccounts)

        runBlocking {
            assertEquals("com.wire.ACTION_NO_USER_LEFT", viewModel.logoutNavigationAction.awaitValue())
        }
    }

    @Test
    fun `given onUserLoggedOut called with status AnotherAccountExists, then updates logoutNavigationAction with ACTION_CURRENT_USER_CHANGED`() {
        viewModel.onUserLoggedOut(AnotherAccountExists)

        runBlocking {
            assertEquals("com.wire.ACTION_CURRENT_USER_CHANGED", viewModel.logoutNavigationAction.awaitValue())
        }
    }

    @Test
    fun `given onUserLogoutError called with a failure, then updates errorLiveData with that failure`() {
        val failure = mock(Failure::class.java)

        viewModel.onUserLogoutError(failure)

        runBlocking {
            assertEquals("Error logging out: $failure", viewModel.errorLiveData.awaitValue())
        }
    }

    companion object {
        private const val TEST_ACCOUNT_CONFIG_URL = "http://www.wire.com"
        private const val TEST_RESET_PASSWORD_URL_SUFFIX = "/forgot/"
        private const val TEST_NAME = "testName"
        private const val TEST_HANDLE = "@Wire"
        private const val TEST_EMAIL = "email@wire.com"
        private const val TEST_PHONE = "+497573889375"
    }
}
