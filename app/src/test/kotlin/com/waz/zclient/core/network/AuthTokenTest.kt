package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class AuthTokenTest : UnitTest() {

    private lateinit var authToken: AuthToken

    private val myToken = "MyToken"

    @Mock private lateinit var tokenRepository: AccessTokenRepository

    @Before
    fun setUp() {
        authToken = AuthToken(tokenRepository)
    }

    @Test fun `should get the access token from the token repository`() {
        authToken.accessToken()
        verify(tokenRepository).accessToken()
        verifyNoMoreInteractions(tokenRepository)
    }

    @Test fun `should update the access token`() {
        authToken.updateAccessToken(myToken)
        verify(tokenRepository).updateAccessToken(myToken)
        verifyNoMoreInteractions(tokenRepository)
    }

    @Test fun `should get the refresh token from the token repository`() {
        authToken.refreshToken()
        verify(tokenRepository).refreshToken()
        verifyNoMoreInteractions(tokenRepository)
    }

    @Test fun `should update the refresh token`() {
        authToken.updateRefreshToken(myToken)
        verify(tokenRepository).updateRefreshToken(myToken)
        verifyNoMoreInteractions(tokenRepository)
    }

    @Test fun `should wipe out tokens`() {
        authToken.wipeOutTokens()
        verify(tokenRepository).wipeOutTokens()
        verifyNoMoreInteractions(tokenRepository)
    }
}
