package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
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
        val newAccessToken = "newAccessToken"
        val refreshToken = "refreshToken"
        `when`(authToken.refreshToken()).thenReturn(refreshToken)
        `when`(authToken.renewAccessToken(refreshToken)).thenReturn(Either.Right(newAccessToken))

        val response = mock(Response::class.java)
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
        verify(reqBuilder).addHeader("Authorization", "Bearer $newAccessToken")
        verify(reqBuilder).build()
        verifyNoMoreInteractions(authToken, reqBuilder)
    }

    @Test
    fun `when renewing access token fails, gives up and returns null request`() {
        val refreshToken = "refreshToken"
        `when`(authToken.refreshToken()).thenReturn(refreshToken)
        `when`(authToken.renewAccessToken(refreshToken)).thenReturn(Either.Left(ServerError))

        val response = mock(Response::class.java)

        val request = accessTokenAuthenticator.authenticate(mock(Route::class.java), response)

        verify(authToken).refreshToken()
        verify(authToken).renewAccessToken(refreshToken)

        request shouldBe null
        verify(authToken, never()).updateAccessToken(any())
        verifyNoMoreInteractions(authToken, response)
    }


}
