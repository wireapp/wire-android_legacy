package com.waz.zclient.core.network.accesstoken

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.network.api.token.AccessTokenResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*

@ExperimentalCoroutinesApi
class AccessTokenRepositoryTest : UnitTest() {

    @Mock
    private lateinit var remoteDataSource: AccessTokenRemoteDataSource

    @Mock
    private lateinit var localDataSource: AccessTokenLocalDataSource

    @Mock
    private lateinit var accessTokenMapper: AccessTokenMapper

    @Mock
    private lateinit var refreshTokenMapper: RefreshTokenMapper

    private lateinit var accessTokenRepository: AccessTokenRepository

    @Before
    fun setUp() {
        accessTokenRepository =
            AccessTokenRepository(remoteDataSource, localDataSource, accessTokenMapper, refreshTokenMapper)
    }

    @Test
    fun `given localDataSource with accessTokenPref, when accessToken called, returns its accessToken mapping`() {
        `when`(localDataSource.accessToken()).thenReturn(ACCESS_TOKEN_PREF)
        `when`(accessTokenMapper.from(ACCESS_TOKEN_PREF)).thenReturn(ACCESS_TOKEN)

        val token = accessTokenRepository.accessToken()

        verify(localDataSource).accessToken()
        token shouldBe ACCESS_TOKEN
    }

    @Test
    fun `given localDataSource doesn't have an accessToken, when accessToken called, returns empty AccessToken`() {
        `when`(localDataSource.accessToken()).thenReturn(null)

        val token = accessTokenRepository.accessToken()

        verify(localDataSource).accessToken()
        token shouldBe AccessToken.EMPTY
    }

    @Test
    fun `when updateAccessToken is called, calls localDataSource with the mapping of given token`() {
        `when`(accessTokenMapper.toPreference(ACCESS_TOKEN)).thenReturn(ACCESS_TOKEN_PREF)

        accessTokenRepository.updateAccessToken(ACCESS_TOKEN)

        verify(accessTokenMapper).toPreference(ACCESS_TOKEN)
        verify(localDataSource).updateAccessToken(ACCESS_TOKEN_PREF)
        verifyNoMoreInteractions(remoteDataSource, localDataSource)
    }

    @Test
    fun `given localDataSource has a refreshToken, when refreshToken called, returns mapping of local refreshToken`() {
        `when`(localDataSource.refreshToken()).thenReturn(REFRESH_TOKEN_PREF)
        `when`(refreshTokenMapper.from(REFRESH_TOKEN_PREF)).thenReturn(REFRESH_TOKEN)

        val token = accessTokenRepository.refreshToken()

        verify(localDataSource).refreshToken()
        token shouldBe REFRESH_TOKEN
    }

    @Test
    fun `given localDataSource doesn't have a refreshToken, when refreshToken called, returns empty RefreshToken`() {
        `when`(localDataSource.refreshToken()).thenReturn(null)

        val token = accessTokenRepository.refreshToken()

        verify(localDataSource).refreshToken()
        token shouldBe RefreshToken.EMPTY
    }

    @Test
    fun `when updateRefreshToken is called, calls localDataSource with mapping of given token`() {
        `when`(refreshTokenMapper.toPreference(REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_PREF)

        accessTokenRepository.updateRefreshToken(REFRESH_TOKEN)

        verify(localDataSource).updateRefreshToken(REFRESH_TOKEN_PREF)
        verifyNoMoreInteractions(remoteDataSource, localDataSource)
    }

    @Test
    fun `when renewAccessToken is called, calls remoteDataSource with given refresh token`() =
        runBlockingTest {
            `when`(accessTokenMapper.from(ACCESS_TOKEN_RESPONSE)).thenReturn(ACCESS_TOKEN)
            `when`(remoteDataSource.renewAccessToken(REFRESH_TOKEN.token)).thenReturn(Either.Right(ACCESS_TOKEN_RESPONSE))

            accessTokenRepository.renewAccessToken(REFRESH_TOKEN)

            verify(remoteDataSource).renewAccessToken(REFRESH_TOKEN.token)
            verifyNoMoreInteractions(remoteDataSource, localDataSource)
        }

    @Test
    fun `given remote call's successful, when renewAccessToken's called, returns mapping of the accessTokenResponse`() =
        runBlockingTest {
            `when`(remoteDataSource.renewAccessToken(REFRESH_TOKEN.token))
                .thenReturn(Either.Right(ACCESS_TOKEN_RESPONSE))
            `when`(accessTokenMapper.from(ACCESS_TOKEN_RESPONSE)).thenReturn(ACCESS_TOKEN)

            val tokenResponse = accessTokenRepository.renewAccessToken(REFRESH_TOKEN)

            verify(accessTokenMapper).from(ACCESS_TOKEN_RESPONSE)
            tokenResponse.isRight shouldBe true
            tokenResponse.map { it shouldBe ACCESS_TOKEN }
        }

    @Test
    fun `given remote call fails, when renewAccessToken is called, returns the error without any mapping`() =
        runBlockingTest {
            `when`(remoteDataSource.renewAccessToken(REFRESH_TOKEN.token)).thenReturn(Either.Left(ServerError))

            val tokenResponse = accessTokenRepository.renewAccessToken(REFRESH_TOKEN)

            verify(accessTokenMapper, never()).from(any<AccessTokenResponse>())
            tokenResponse.isLeft shouldBe true
            tokenResponse.onFailure { it shouldBe ServerError }
            verifyNoMoreInteractions(accessTokenMapper)
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

        private val REFRESH_TOKEN = RefreshToken("refreshToken")
        private val REFRESH_TOKEN_PREF = RefreshTokenPreference("refreshToken")
    }
}
