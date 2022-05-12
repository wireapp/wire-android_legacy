package com.waz.zclient.core.network.api.token

import com.waz.zclient.UnitTest
import com.waz.zclient.capture
import com.waz.zclient.core.network.NetworkHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.`should contain`
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

//TODO: try to use runBlockingTest once the issue with threading solved:
//https://github.com/Kotlin/kotlinx.coroutines/issues/1222
//https://github.com/Kotlin/kotlinx.coroutines/issues/1204
class TokenServiceTest : UnitTest() {

    @Mock
    private lateinit var networkHandler: NetworkHandler

    @Mock
    private lateinit var tokenApi: TokenApi

    @Captor
    private lateinit var cookieHeaderCaptor : ArgumentCaptor<Map<String, String>>

    @Captor
    private lateinit var tokenQueryCaptor : ArgumentCaptor<Map<String, String>>

    private lateinit var tokenService: TokenService

    @Before
    fun setUp() {
        `when`(networkHandler.isConnected).thenReturn(true)
        tokenService = TokenService(networkHandler, tokenApi)
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `given refresh token, when renewing access token, adds refresh token as a header to request`() {
        runBlocking {
            val refreshToken = "testToken"

            tokenService.renewAccessToken(refreshToken)

            verify(tokenApi).access(capture(cookieHeaderCaptor))
            cookieHeaderCaptor.value `should contain` ("Cookie" to "zuid=$refreshToken")
        }
    }

    @Test
    fun `given access and refresh tokens, when logout is called, calls tokenApi with correct header and query`() {
        runBlocking {
            val refreshToken = "refreshToken"
            val accessToken = "accessToken"

            tokenService.logout(refreshToken, accessToken)

            verify(tokenApi).logout(capture(cookieHeaderCaptor), capture(tokenQueryCaptor))
            cookieHeaderCaptor.value `should contain` ("Cookie" to "zuid=$refreshToken")
            tokenQueryCaptor.value `should contain` ("access_token" to accessToken)
        }
    }
}
