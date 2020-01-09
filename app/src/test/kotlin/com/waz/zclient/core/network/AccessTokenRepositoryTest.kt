package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import com.waz.zclient.core.extension.empty
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class AccessTokenRepositoryTest : UnitTest() {

    @Mock
    private lateinit var remoteDataSource: AccessTokenRemoteDataSource

    @Mock
    private lateinit var localDataSource: AccessTokenLocalDataSource

    private lateinit var accessTokenRepository: AccessTokenRepository

    @Before
    fun setUp() {
        accessTokenRepository = AccessTokenRepository(remoteDataSource, localDataSource)
    }

    @Test
    fun `given localDataSource has an accessToken, when accessToken called, returns it`() {
        //FIXME Create access token data class for repository
//        `when`(localDataSource.accessToken()).thenReturn(ACCESS_TOKEN)
//
//        val token = accessTokenRepository.accessToken()
//
//        verify(localDataSource).accessToken()
//        token shouldBe ACCESS_TOKEN
    }

    @Test
    fun `given localDataSource doesn't have an accessToken, when accessToken called, returns empty string`() {
        `when`(localDataSource.accessToken()).thenReturn(null)

        val token = accessTokenRepository.accessToken()

        verify(localDataSource).accessToken()
        token shouldBe String.empty()
    }

    @Test
    fun `when updateAccessToken is called, calls localDataSource with given token`() {
        //FIXME Create access token data class for repository
//        accessTokenRepository.updateAccessToken(NEW_TOKEN)
//
//        verify(localDataSource).updateAccessToken(NEW_TOKEN)
//        verifyNoMoreInteractions(remoteDataSource, localDataSource)
    }

    @Test
    fun `given localDataSource has a refreshToken, when refreshToken called, returns it`() {
        `when`(localDataSource.refreshToken()).thenReturn(REFRESH_TOKEN)

        val token = accessTokenRepository.refreshToken()

        verify(localDataSource).refreshToken()
        token shouldBe REFRESH_TOKEN
    }

    @Test
    fun `given localDataSource doesn't have a refreshToken, when refreshToken called, returns empty string`() {
        `when`(localDataSource.refreshToken()).thenReturn(null)

        val token = accessTokenRepository.refreshToken()

        verify(localDataSource).refreshToken()
        token shouldBe String.empty()
    }

    @Test
    fun `when updateRefreshToken is called, calls localDataSource with given token`() {
        accessTokenRepository.updateRefreshToken(NEW_TOKEN)

        verify(localDataSource).updateRefreshToken(NEW_TOKEN)
        verifyNoMoreInteractions(remoteDataSource, localDataSource)
    }

    @Test
    fun `when renewAccessToken is called, calls remoteDataSource with given refresh token`() {
        accessTokenRepository.renewAccessToken(REFRESH_TOKEN)

        verify(remoteDataSource).renewAccessToken(REFRESH_TOKEN)
        verifyNoMoreInteractions(remoteDataSource, localDataSource)
    }

    @Test
    fun `when wipeOutTokens is called, wipes out both access and refresh tokens`() {
        accessTokenRepository.wipeOutTokens()

        verify(localDataSource).wipeOutAccessToken()
        verify(localDataSource).wipeOutRefreshToken()
        verifyNoMoreInteractions(localDataSource, remoteDataSource)
    }

    companion object {
        private val ACCESS_TOKEN = AccessTokenPreference("token", "tokenType", "expiresIn")
        private const val REFRESH_TOKEN = "refreshToken"
        private const val NEW_TOKEN = "newToken"
    }
}
