package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.network.api.token.AccessTokenResponse
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class AccessTokenRepositoryTest : UnitTest() {

    @Mock
    private lateinit var remoteDataSource: AccessTokenRemoteDataSource

    @Mock
    private lateinit var localDataSource: AccessTokenLocalDataSource

    @Mock
    private lateinit var mapper: AccessTokenMapper

    private lateinit var accessTokenRepository: AccessTokenRepository

    @Before
    fun setUp() {
        accessTokenRepository = AccessTokenRepository(remoteDataSource, localDataSource, mapper)
    }

    @Test
    fun `given localDataSource with accessTokenPref, when accessToken called, returns its accessToken mapping`() {
        `when`(localDataSource.accessToken()).thenReturn(ACCESS_TOKEN_PREF)
        `when`(mapper.from(ACCESS_TOKEN_PREF)).thenReturn(ACCESS_TOKEN)

        val token = accessTokenRepository.accessToken()

        verify(localDataSource).accessToken()
        token shouldBe ACCESS_TOKEN
    }

    @Test
    fun `given localDataSource doesn't have an accessToken, when accessToken called, returns null`() {
        `when`(localDataSource.accessToken()).thenReturn(null)

        val token = accessTokenRepository.accessToken()

        verify(localDataSource).accessToken()
        token shouldBe null
    }

    @Test
    fun `when updateAccessToken is called, calls localDataSource with the mapping of given token`() {
        `when`(mapper.toPreference(ACCESS_TOKEN)).thenReturn(ACCESS_TOKEN_PREF)

        accessTokenRepository.updateAccessToken(ACCESS_TOKEN)

        verify(mapper).toPreference(ACCESS_TOKEN)
        verify(localDataSource).updateAccessToken(ACCESS_TOKEN_PREF)
        verifyNoMoreInteractions(remoteDataSource, localDataSource)
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
        `when`(mapper.from(ACCESS_TOKEN_RESPONSE)).thenReturn(ACCESS_TOKEN)
        `when`(remoteDataSource.renewAccessToken(REFRESH_TOKEN)).thenReturn(Either.Right(ACCESS_TOKEN_RESPONSE))

        accessTokenRepository.renewAccessToken(REFRESH_TOKEN)

        verify(remoteDataSource).renewAccessToken(REFRESH_TOKEN)
        verifyNoMoreInteractions(remoteDataSource, localDataSource)
    }

    @Test
    fun `given remote call is successful, when renewAccessToken is called, returns mapping of the accessTokenResponse`
        () {
        `when`(remoteDataSource.renewAccessToken(any())).thenReturn(Either.Right(ACCESS_TOKEN_RESPONSE))
        `when`(mapper.from(ACCESS_TOKEN_RESPONSE)).thenReturn(ACCESS_TOKEN)

        val tokenResponse = accessTokenRepository.renewAccessToken(REFRESH_TOKEN)

        verify(mapper).from(ACCESS_TOKEN_RESPONSE)
        tokenResponse.isRight shouldBe true
        tokenResponse.map { it shouldBe ACCESS_TOKEN }
    }

    @Test
    fun `given remote call fails, when renewAccessToken is called, returns the error without any mapping`() {
        `when`(remoteDataSource.renewAccessToken(any())).thenReturn(Either.Left(ServerError))

        val tokenResponse = accessTokenRepository.renewAccessToken(REFRESH_TOKEN)

        verify(mapper, never()).from(any<AccessTokenResponse>())
        tokenResponse.isLeft shouldBe true
        tokenResponse.onFailure { it shouldBe ServerError }
        verifyNoMoreInteractions(mapper)
    }

    @Test
    fun `when wipeOutTokens is called, wipes out both access and refresh tokens`() {
        accessTokenRepository.wipeOutTokens()

        verify(localDataSource).wipeOutAccessToken()
        verify(localDataSource).wipeOutRefreshToken()
        verifyNoMoreInteractions(localDataSource, remoteDataSource)
    }

    companion object {
        private val ACCESS_TOKEN = AccessToken("newToken", "newType", "newExpiry")
        private val ACCESS_TOKEN_PREF =
            AccessTokenPreference("token", "tokenType", "expiresIn")
        private val ACCESS_TOKEN_RESPONSE =
            AccessTokenResponse("respToken", "respType", "respUser", "respExpiry")

        private const val REFRESH_TOKEN = "refreshToken"
        private const val NEW_TOKEN = "newToken"
    }
}
