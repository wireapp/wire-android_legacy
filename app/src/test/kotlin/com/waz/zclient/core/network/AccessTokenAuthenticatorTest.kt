package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
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

class AccessTokenAuthenticatorTest : UnitTest() {

    private lateinit var accessTokenAuthenticator: AccessTokenAuthenticator

    @Mock private lateinit var authToken: AuthTokenHandler

    @Before
    fun setUp() {
        accessTokenAuthenticator = AccessTokenAuthenticator(authToken)
    }

    @Test
    fun `when renewing access token is successful, adds new access token to header`() {
        val newAccessToken = AccessToken("newToken", "newType","newExpiry")
        val refreshToken = "refreshToken"
        `when`(authToken.refreshToken()).thenReturn(refreshToken)
        `when`(authToken.renewAccessToken(refreshToken)).thenReturn(Either.Right(newAccessToken))

        val response = mockResponse()
        val request = mock(Request::class.java)
        `when`(response.request()).thenReturn(request)
        `when`(request.header("Authorization")).thenReturn("expiredToken")

        val reqBuilder = mock(Request.Builder::class.java)
        `when`(request.newBuilder()).thenReturn(reqBuilder)
        `when`(reqBuilder.removeHeader(any())).thenReturn(reqBuilder)
        `when`(reqBuilder.addHeader(any(), any())).thenReturn(reqBuilder)

        accessTokenAuthenticator.authenticate(mock(Route::class.java), response)

        verify(authToken).refreshToken()
        verify(authToken).renewAccessToken(refreshToken)
        verify(authToken).updateAccessToken(newAccessToken)
        verify(request).header("Authorization")
        verify(reqBuilder).removeHeader("Authorization")
        verify(reqBuilder).addHeader("Authorization", "Bearer ${newAccessToken.token}")
        verify(reqBuilder).build()
        verifyNoMoreInteractions(authToken, reqBuilder)
    }

    @Test
    fun `when renewing access token fails, gives up and returns null request`() {
        val refreshToken = "refreshToken"
        `when`(authToken.refreshToken()).thenReturn(refreshToken)
        `when`(authToken.renewAccessToken(refreshToken)).thenReturn(Either.Left(ServerError))

        val response = mockResponse()

        val request = accessTokenAuthenticator.authenticate(mock(Route::class.java), response)

        verify(response).headers()
        verify(authToken).refreshToken()
        verify(authToken).renewAccessToken(refreshToken)

        request shouldBe null
        verify(authToken, never()).updateAccessToken(any())
        verifyNoMoreInteractions(authToken, response)
    }

    @Test
    fun `when response returns a "Cookie" header different than current refresh token, updates refresh token`() {
        val oldRefreshToken = "refreshToken"
        val newRefreshToken = "newRefreshToken"

        `when`(authToken.refreshToken()).thenReturn(oldRefreshToken)
        val response = mockResponse(refreshToken = newRefreshToken)
        //bypass adding headers to request. we're not interested
        `when`(authToken.renewAccessToken(any())).thenReturn(Either.Left(ServerError))

        accessTokenAuthenticator.authenticate(mock(Route::class.java), response)

        verify(authToken, times(2)).refreshToken()
        verify(authToken).updateRefreshToken(newRefreshToken)
        verify(authToken, never()).updateRefreshToken(oldRefreshToken)
    }

    @Test
    fun `when response returns a "Cookie" header same as current refresh token, does not update refresh token`() {
        val refreshToken = "refreshToken"

        `when`(authToken.refreshToken()).thenReturn(refreshToken)
        val response = mockResponse(refreshToken = refreshToken)
        //bypass adding headers to request. we're not interested
        `when`(authToken.renewAccessToken(any())).thenReturn(Either.Left(ServerError))

        accessTokenAuthenticator.authenticate(mock(Route::class.java), response)

        verify(authToken, times(2)).refreshToken()
        verify(authToken, never()).updateRefreshToken(any())
    }

    @Test
    fun `when response does not return a "Cookie" header, does not attempt to update refresh token`() {
        `when`(authToken.refreshToken()).thenReturn("refreshToken")
        val response = mockResponse(refreshToken = null)
        //bypass adding headers to request. we're not interested
        `when`(authToken.renewAccessToken(any())).thenReturn(Either.Left(ServerError))

        accessTokenAuthenticator.authenticate(mock(Route::class.java), response)

        verify(authToken).refreshToken()
        verify(authToken, never()).updateRefreshToken(any())
    }

    private fun mockResponse(refreshToken: String? = null): Response {
        val response = mock(Response::class.java)
        val headers = mock(Headers::class.java)
        `when`(response.headers()).thenReturn(headers)
        `when`(headers["Cookie"]).thenReturn(refreshToken)
        return response
    }

}
