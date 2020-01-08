package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import com.waz.zclient.core.network.api.token.TokenService
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify

class AccessTokenRemoteDataSourceTest : UnitTest() {

    @Mock
    private lateinit var tokenService: TokenService

    private lateinit var remoteDataSource: AccessTokenRemoteDataSource

    @Before
    fun setUp() {
        remoteDataSource = AccessTokenRemoteDataSource(tokenService)
    }

    @Test
    fun `given a refresh token, when renewAccessToken is called, calls tokenService with refresh token`() {
        val refreshToken = "testRefreshToken"
        remoteDataSource.renewAccessToken(refreshToken)
        verify(tokenService).renewAccessToken(refreshToken)
    }
}
