package com.waz.zclient.feature.settings.account.logout

import com.waz.zclient.UnitTest
import com.waz.zclient.core.network.accesstoken.AccessToken
import com.waz.zclient.core.network.accesstoken.AccessTokenRepository
import com.waz.zclient.core.network.accesstoken.RefreshToken
import com.waz.zclient.shared.accounts.AccountsRepository
import com.waz.zclient.storage.pref.global.GlobalPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.mock
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class LogoutUseCaseTest : UnitTest() {

    @Mock
    private lateinit var globalPreferences: GlobalPreferences

    @Mock
    private lateinit var accountsRepository: AccountsRepository

    @Mock
    private lateinit var accessTokenRepository: AccessTokenRepository

    private lateinit var logoutUseCase: LogoutUseCase

    @Before
    fun setUp() {
        logoutUseCase = LogoutUseCase(globalPreferences, accountsRepository, accessTokenRepository)
    }

    @Test
    fun `given accessTokenRepo, when run is called, then calls accountsRepo's logout method with access and refresh tokens`() =
        runBlockingTest {
            val accessToken = mock(AccessToken::class)
            `when`(accessToken.token).thenReturn(ACCESS_TOKEN_STRING)
            val refreshToken = mock(RefreshToken::class)
            `when`(refreshToken.token).thenReturn(REFRESH_TOKEN_STRING)

            `when`(accessTokenRepository.accessToken()).thenReturn(accessToken)
            `when`(accessTokenRepository.refreshToken()).thenReturn(refreshToken)

            logoutUseCase.run(Unit)

            verify(accessTokenRepository).accessToken()
            verify(accessTokenRepository).refreshToken()

            verify(accountsRepository).logout(REFRESH_TOKEN_STRING, ACCESS_TOKEN_STRING)
        }

    companion object {
        private const val ACCESS_TOKEN_STRING = "accessToken"
        private const val REFRESH_TOKEN_STRING = "refreshToken"
    }
}
