package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class AuthTokenHandlerTest : UnitTest() {

    private lateinit var authTokenHandler: AuthTokenHandler

    private val myToken = "MyToken"

    @Mock private lateinit var tokenRepository: AccessTokenRepository

    @Before
    fun setUp() {
        authTokenHandler = AuthTokenHandler(tokenRepository)
    }

    @Test fun `should get the access token from the token repository`() {
        authTokenHandler.accessToken()
        verify(tokenRepository).accessToken()
        verifyNoMoreInteractions(tokenRepository)
    }

    @Test fun `should update the access token`() {
        authTokenHandler.updateAccessToken(myToken)
        verify(tokenRepository).updateAccessToken(myToken)
        verifyNoMoreInteractions(tokenRepository)
    }

    @Test fun `should get the refresh token from the token repository`() {
        authTokenHandler.refreshToken()
        verify(tokenRepository).refreshToken()
        verifyNoMoreInteractions(tokenRepository)
    }

    @Test fun `should update the refresh token`() {
        authTokenHandler.updateRefreshToken(myToken)
        verify(tokenRepository).updateRefreshToken(myToken)
        verifyNoMoreInteractions(tokenRepository)
    }

    @Test fun `should wipe out tokens`() {
        authTokenHandler.wipeOutTokens()
        verify(tokenRepository).wipeOutTokens()
        verifyNoMoreInteractions(tokenRepository)
    }
}
