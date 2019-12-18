package com.waz.zclient.core.network.api.token

import com.waz.zclient.UnitTest
import com.waz.zclient.core.network.ApiService
import com.waz.zclient.framework.mockito.any
import com.waz.zclient.framework.mockito.capture
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import retrofit2.Call

class TokenServiceTest : UnitTest() {

    private lateinit var tokenService: TokenService

    @Mock
    private lateinit var apiService: ApiService

    @Mock
    private lateinit var tokenApi: TokenApi

    @Captor
    private lateinit var accessHeadersCaptor : ArgumentCaptor<Map<String, String>>

    @Before
    fun setUp() {
        tokenService = TokenService(tokenApi, apiService)
    }

    @Test
    fun `given refresh token, when renewAccessToken is called, adds refresh token to header`() {
        val refreshToken = "testRefreshToken"
        val networkCall = mock(Call::class.java)
        `when`(tokenApi.access(any())).thenReturn(networkCall as Call<AccessTokenResponse>)

        tokenService.renewAccessToken(refreshToken)

        verify(tokenApi).access(capture(accessHeadersCaptor))
        assert(accessHeadersCaptor.value.containsKey("Cookie"))
        assert(accessHeadersCaptor.value["Cookie"]?.equals("zuid=$refreshToken") ?: false)
    }
}
