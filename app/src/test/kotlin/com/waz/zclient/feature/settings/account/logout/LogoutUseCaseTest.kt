package com.waz.zclient.feature.settings.account.logout

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.network.accesstoken.AccessToken
import com.waz.zclient.core.network.accesstoken.AccessTokenRepository
import com.waz.zclient.core.network.accesstoken.RefreshToken
import com.waz.zclient.shared.accounts.AccountsRepository
import com.waz.zclient.shared.accounts.ActiveAccount
import com.waz.zclient.shared.user.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class LogoutUseCaseTest : UnitTest() {

    @Mock
    private lateinit var accountsRepository: AccountsRepository

    @Mock
    private lateinit var accessTokenRepository: AccessTokenRepository

    @Mock
    private lateinit var usersRepository: UsersRepository

    @Mock
    private lateinit var accessToken: AccessToken

    @Mock
    private lateinit var refreshToken: RefreshToken

    private lateinit var logoutUseCase: LogoutUseCase

    @Before
    fun setUp() {
        `when`(accessToken.token).thenReturn(ACCESS_TOKEN_STRING)
        `when`(refreshToken.token).thenReturn(REFRESH_TOKEN_STRING)
        runBlocking {
            `when`(accessTokenRepository.accessToken()).thenReturn(accessToken)
            `when`(accessTokenRepository.refreshToken()).thenReturn(refreshToken)
            `when`(accountsRepository.activeAccounts()).thenReturn(Either.Right(emptyList()))
        }
        `when`(usersRepository.currentUserId()).thenReturn(TEST_USER_ID)

        logoutUseCase = LogoutUseCase(accountsRepository, accessTokenRepository, usersRepository)
    }

    @Test
    fun `given accessTokenRepo, when run is called, then calls accountsRepo's logout method with correct tokens from accessTokenRepo`() =
        runBlockingTest {
            logoutUseCase.run(Unit)

            verify(accessTokenRepository).accessToken()
            verify(accessTokenRepository).refreshToken()

            verify(accountsRepository).logout(REFRESH_TOKEN_STRING, ACCESS_TOKEN_STRING)
        }

    @Test
    fun `given use case is run, when logout operation returns success, then proceeds to account deletion`() =
        runBlockingTest {
            `when`(accountsRepository.logout(any(), any())).thenReturn(Either.Right(Unit))

            logoutUseCase.run(Unit)

            verify(accountsRepository).deleteAccountFromDevice(TEST_USER_ID)
        }

    @Test
    fun `given use case is run, when logout operation fails, then proceeds to account deletion`() =
        runBlockingTest {
            `when`(accountsRepository.logout(any(), any())).thenReturn(Either.Left(Forbidden))

            logoutUseCase.run(Unit)

            verify(accountsRepository).deleteAccountFromDevice(TEST_USER_ID)
        }

    @Test
    fun `given use case is run, when account deletion is successful, then proceeds to checking remaining accounts`() =
        runBlockingTest {
            `when`(accountsRepository.deleteAccountFromDevice(TEST_USER_ID)).thenReturn(Either.Right(Unit))

            logoutUseCase.run(Unit)

            verify(accountsRepository).activeAccounts()
        }

    @Test
    fun `given use case is run, when account deletion fails, then proceeds to checking remaining accounts`() =
        runBlockingTest {
            `when`(accountsRepository.deleteAccountFromDevice(TEST_USER_ID)).thenReturn(Either.Left(DatabaseError))

            logoutUseCase.run(Unit)

            verify(accountsRepository).activeAccounts()
        }

    @Test
    fun `given account is deleted and there's no accounts left, then clears currentUserId and returns NoAccountsLeft`() =
        runBlockingTest {
            `when`(accountsRepository.activeAccounts()).thenReturn(Either.Right(emptyList()))

            val result = logoutUseCase.run(Unit)

            result.isRight shouldBe true
            result.map { it shouldBe NoAccountsLeft }
            verify(usersRepository).setCurrentUserId(String.empty())
        }

    @Test
    fun `given account is deleted and there's another account left, then updates currentUserId with new id and returns AnotherAccountExists`() =
        runBlockingTest {
            val id = "otherAccountId"
            val otherAccount = mockAccount(id)
            `when`(accountsRepository.activeAccounts()).thenReturn(Either.Right(listOf(otherAccount)))

            val result = logoutUseCase.run(Unit)

            result.isRight shouldBe true
            result.map { it shouldBe AnotherAccountExists }
            verify(usersRepository).setCurrentUserId(id)
        }

    @Test
    fun `given account deletion fails and the account still exists, when there's no other account, then clears currentUserId and returns NoAccountsLeft`() =
        runBlockingTest {
            val activeAccount = mockAccount(TEST_USER_ID)
            `when`(accountsRepository.activeAccounts()).thenReturn(Either.Right(listOf(activeAccount)))

            val result = logoutUseCase.run(Unit)

            result.isRight shouldBe true
            result.map { it shouldBe NoAccountsLeft }
            verify(usersRepository).setCurrentUserId(String.empty())
        }

    @Test
    fun `given account deletion fails and the account still exists, when there's another account, then updates currentUserId and returns AnotherAccountExists`() =
        runBlockingTest {
            val loggedOutAccount = mockAccount(TEST_USER_ID)
            val newId = "newActiveId"
            val newActiveAccount = mockAccount(newId)
            `when`(accountsRepository.activeAccounts()).thenReturn(Either.Right(listOf(loggedOutAccount, newActiveAccount)))

            val result = logoutUseCase.run(Unit)

            result.isRight shouldBe true
            result.map { it shouldBe AnotherAccountExists }
            verify(usersRepository).setCurrentUserId(newId)
        }

    @Test
    fun `given remaining active accounts check fails, then clears current user id and returns CouldNotReadRemainingAccounts`() =
        runBlockingTest {
            val failure = mock(Failure::class.java)
            `when`(accountsRepository.activeAccounts()).thenReturn(Either.Left(failure))

            val result = logoutUseCase.run(Unit)

            result.isRight shouldBe true
            result.map { it shouldBe CouldNotReadRemainingAccounts }
            verify(usersRepository).setCurrentUserId(String.empty())
        }

    companion object {
        private const val TEST_USER_ID = "testUser_id"
        private const val ACCESS_TOKEN_STRING = "accessToken"
        private const val REFRESH_TOKEN_STRING = "refreshToken"

        private fun mockAccount(id: String): ActiveAccount {
            val account = mock(ActiveAccount::class.java)
            `when`(account.id).thenReturn(id)
            return account
        }
    }
}
