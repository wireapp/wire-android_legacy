package com.waz.zclient.core.network.accesstoken

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

@ExperimentalCoroutinesApi
class AccessTokenAuthenticatorTest : UnitTest() {

    private lateinit var accessTokenAuthenticator: AccessTokenAuthenticator

    @Mock
    private lateinit var repository: AccessTokenRepository

    @Mock
    private lateinit var mapper: RefreshTokenMapper

    @Before
    fun setUp() {
        accessTokenAuthenticator = AccessTokenAuthenticator(repository, mapper)
    }

    @Test
    fun `when renewing access token is successful, adds new access token to header`() = runBlockingTest {
        `when`(repository.refreshToken()).thenReturn(REFRESH_TOKEN)
        `when`(repository.renewAccessToken(REFRESH_TOKEN)).thenReturn(Either.Right(ACCESS_TOKEN))

        val response = mockResponse()
        val request = mock(Request::class.java)
        `when`(response.request()).thenReturn(request)
        `when`(request.header("Authorization")).thenReturn("expiredToken")

        val reqBuilder = mock(Request.Builder::class.java)
        `when`(request.newBuilder()).thenReturn(reqBuilder)
        `when`(reqBuilder.removeHeader(any())).thenReturn(reqBuilder)
        `when`(reqBuilder.addHeader(any(), any())).thenReturn(reqBuilder)

        accessTokenAuthenticator.authenticate(mock(Route::class.java), response)

        verify(repository).refreshToken()
        verify(repository).renewAccessToken(REFRESH_TOKEN)
        verify(repository).updateAccessToken(ACCESS_TOKEN)
        verify(request).header("Authorization")
        verify(reqBuilder).removeHeader("Authorization")
        verify(reqBuilder).addHeader("Authorization", "Bearer ${ACCESS_TOKEN.token}")
        verify(reqBuilder).build()
        verifyNoMoreInteractions(repository, reqBuilder)
    }

    @Test
    fun `when renewing access token fails, gives up and returns null request`() = runBlockingTest {
        `when`(repository.refreshToken()).thenReturn(REFRESH_TOKEN)
        `when`(repository.renewAccessToken(REFRESH_TOKEN)).thenReturn(Either.Left(ServerError))

        val response = mockResponse()

        val request = accessTokenAuthenticator.authenticate(mock(Route::class.java), response)

        verify(response).headers()
        verify(repository).refreshToken()
        verify(repository).renewAccessToken(REFRESH_TOKEN)

        request shouldBe null
        verify(repository, never()).updateAccessToken(any())
        verifyNoMoreInteractions(repository, response)
    }

    @Test
    fun `when response returns a "Cookie" header different than current refresh token, updates refresh token`() =
        runBlockingTest {
            val newToken = "newRefreshToken"
            val newRefreshToken = RefreshToken(newToken)

            `when`(repository.refreshToken()).thenReturn(REFRESH_TOKEN)
            `when`(mapper.fromTokenText(any())).thenReturn(newRefreshToken)

            val response = mockResponse(refreshToken = newToken)
            //bypass adding headers to request. we're not interested
            `when`(repository.renewAccessToken(any())).thenReturn(Either.Left(ServerError))

            accessTokenAuthenticator.authenticate(mock(Route::class.java), response)

            verify(repository, times(2)).refreshToken()
            verify(repository).updateRefreshToken(newRefreshToken)
            verify(repository, never()).updateRefreshToken(REFRESH_TOKEN)
        }

    @Test
    fun `when response returns a "Cookie" header same as current RefreshToken's token, does not update current one`() =
        runBlockingTest {
            `when`(repository.refreshToken()).thenReturn(REFRESH_TOKEN)
            `when`(mapper.fromTokenText(REFRESH_TOKEN.token)).thenReturn(RefreshToken(REFRESH_TOKEN.token))

            val response = mockResponse(refreshToken = REFRESH_TOKEN.token)
            //bypass adding headers to request. we're not interested
            `when`(repository.renewAccessToken(any())).thenReturn(Either.Left(ServerError))

            accessTokenAuthenticator.authenticate(mock(Route::class.java), response)

            verify(repository, times(2)).refreshToken()
            verify(repository, never()).updateRefreshToken(any())
        }

    @Test
    fun `when response does not return a "Cookie" header, does not attempt to update refresh token`() =
        runBlockingTest {
            `when`(repository.refreshToken()).thenReturn(REFRESH_TOKEN)
            val response = mockResponse(refreshToken = null)
            //bypass adding headers to request. we're not interested
            `when`(repository.renewAccessToken(any())).thenReturn(Either.Left(ServerError))

            accessTokenAuthenticator.authenticate(mock(Route::class.java), response)

            verify(repository).refreshToken()
            verify(repository, never()).updateRefreshToken(any())
        }

    private fun mockResponse(refreshToken: String? = null): Response {
        val response = mock(Response::class.java)
        val headers = mock(Headers::class.java)
        `when`(response.headers()).thenReturn(headers)
        `when`(headers["Cookie"]).thenReturn(refreshToken)
        return response
    }

    companion object {
        val REFRESH_TOKEN = RefreshToken("refreshToken")
        val ACCESS_TOKEN = AccessToken("accessToken", "tokenType", "expiresIn")
    }

}
