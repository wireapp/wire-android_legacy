package com.waz.zclient.shared.accounts.datasources.remote

import com.waz.zclient.UnitTest
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.core.network.api.token.TokenService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class AccountsRemoteDataSourceTest : UnitTest() {

    @Mock
    private lateinit var tokenService: TokenService

    private lateinit var accountsRemoteDataSource: AccountsRemoteDataSource

    @Before
    fun setUp() {
        accountsRemoteDataSource = AccountsRemoteDataSource(tokenService, mock(NetworkHandler::class.java))
    }

    @Test
    fun `given refresh and access tokens, when logout is called, calls tokenService's logout method`() =
        runBlockingTest {
            val refreshToken = "refreshToken"
            val accessToken = "accessToken"

            accountsRemoteDataSource.logout(refreshToken, accessToken)

            verify(tokenService).logout(refreshToken, accessToken)
        }
}
