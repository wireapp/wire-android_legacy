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

    private lateinit var tokenService: TokenService

    @Before
    fun setUp() {
        tokenService = TokenService(networkHandler, tokenApi)
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `given refresh token, when renewing access token, adds refresh token as a header to request`() {
        runBlocking {
            val refreshToken = "testToken"
            `when`(networkHandler.isConnected).thenReturn(true)

            tokenService.renewAccessToken(refreshToken)

            val argumentCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
            verify(tokenApi).access(capture(argumentCaptor))
            argumentCaptor.value `should contain` ("Cookie" to "zuid=$refreshToken")
        }
    }
}
