package com.waz.zclient.core.network.api.token

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.capture
import com.waz.zclient.core.network.ApiService
import org.amshove.kluent.`should contain`
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.verify

class TokenServiceTest : UnitTest() {

    @Mock
    private lateinit var apiService: ApiService

    @Mock
    private lateinit var tokenApi: TokenApi

    private lateinit var tokenService: TokenService

    @Before
    fun setUp() {
        tokenService = TokenService(apiService, tokenApi)
    }

    @Test
    fun `given refresh token, when renewing access token, adds refresh token as a header to request`() {
        val refreshToken = "testToken"

        tokenService.renewAccessToken(refreshToken)

        verify(apiService).request(any(), any<AccessTokenResponse>())

        val argumentCaptor = ArgumentCaptor.forClass(Map::class.java) as ArgumentCaptor<Map<String, String>>
        verify(tokenApi).access(capture(argumentCaptor))
        argumentCaptor.value `should contain` ("Cookie" to "zuid=$refreshToken")
    }

}
