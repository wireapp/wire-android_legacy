package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.extension.empty
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class AccessTokenInterceptorTest : UnitTest() {

    @Mock
    lateinit var authTokenHandler: AuthTokenHandler

    private lateinit var accessTokenInterceptor: AccessTokenInterceptor

    @Mock
    private lateinit var chain: Interceptor.Chain

    @Mock
    private lateinit var request: Request

    @Before
    fun setUp() {
        `when`(chain.request()).thenReturn(request)
        accessTokenInterceptor = AccessTokenInterceptor(authTokenHandler)
    }

    @Test
    fun `if there's an access token, adds it to request's header`() {
        `when`(authTokenHandler.accessToken()).thenReturn(ACCESS_TOKEN)

        val requestBuilder = mock(Request.Builder::class.java)
        val requestWithHeader = mockRequestWithAuthHeader(requestBuilder)
        val response = mockResponse()
        `when`(chain.proceed(requestWithHeader)).thenReturn(response)

        accessTokenInterceptor.intercept(chain)

        verify(authTokenHandler).accessToken()
        verify(requestBuilder).addHeader("Authorization", "Bearer $ACCESS_TOKEN")
        verify(chain).proceed(requestWithHeader)
        verify(requestBuilder, never()).removeHeader("Authorization")
    }

    @Test
    fun `if access token is empty, doesn't add authorization header`() {
        `when`(authTokenHandler.accessToken()).thenReturn(String.empty())
        val response = mockResponse()
        `when`(chain.proceed(request)).thenReturn(response)

        accessTokenInterceptor.intercept(chain)

        verify(chain).proceed(request)
        //also verify that there's no attempt to create a new request
        verifyNoMoreInteractions(request)
    }

    @Test
    fun `when response returns a "Cookie" header different than current refresh token, updates refresh token`() {
        `when`(authTokenHandler.accessToken()).thenReturn(ACCESS_TOKEN)
        `when`(authTokenHandler.refreshToken()).thenReturn(REFRESH_TOKEN)
        val requestWithHeader = mockRequestWithAuthHeader()
        val response = mockResponse(cookie = NEW_REFRESH_TOKEN)
        `when`(chain.proceed(requestWithHeader)).thenReturn(response)

        accessTokenInterceptor.intercept(chain)

        verify(authTokenHandler).accessToken()
        verify(response).headers()
        verify(authTokenHandler).refreshToken()
        verify(authTokenHandler).updateRefreshToken(NEW_REFRESH_TOKEN)
        verifyNoMoreInteractions(authTokenHandler)
    }

    @Test
    fun `when response returns a "Cookie" header same as current refresh token, does not update refresh token`() {
        `when`(authTokenHandler.accessToken()).thenReturn(ACCESS_TOKEN)
        `when`(authTokenHandler.refreshToken()).thenReturn(REFRESH_TOKEN)
        val requestWithHeader = mockRequestWithAuthHeader()
        val response = mockResponse(cookie = REFRESH_TOKEN)
        `when`(chain.proceed(requestWithHeader)).thenReturn(response)

        accessTokenInterceptor.intercept(chain)

        verify(authTokenHandler).accessToken()
        verify(response).headers()
        verify(authTokenHandler).refreshToken()
        verify(authTokenHandler, never()).updateRefreshToken(any())
        verifyNoMoreInteractions(authTokenHandler)
    }

    @Test
    fun `when response does not return a "Cookie" header, does not attempt to update refresh token`() {
        `when`(authTokenHandler.accessToken()).thenReturn(ACCESS_TOKEN)
        val requestWithHeader = mockRequestWithAuthHeader()
        val response = mockResponse()
        `when`(chain.proceed(requestWithHeader)).thenReturn(response)

        accessTokenInterceptor.intercept(chain)

        verify(authTokenHandler).accessToken()
        verify(response).headers()
        verify(authTokenHandler, never()).refreshToken()
        verify(authTokenHandler, never()).updateRefreshToken(any())
        verifyNoMoreInteractions(authTokenHandler)
    }

    private fun mockRequestWithAuthHeader(
        requestBuilder: Request.Builder = mock(Request.Builder::class.java)
    ): Request {
        `when`(request.newBuilder()).thenReturn(requestBuilder)
        `when`(requestBuilder.addHeader(any(), any())).thenReturn(requestBuilder)

        val requestWithHeader = mock(Request::class.java)
        `when`(requestBuilder.build()).thenReturn(requestWithHeader)

        return requestWithHeader
    }

    private fun mockResponse(cookie: String? = null): Response {
        val response = mock(Response::class.java)
        val headers = mock(Headers::class.java)
        `when`(response.headers()).thenReturn(headers)
        `when`(headers[COOKIE_HEADER_NAME]).thenReturn(cookie)
        return response
    }

    companion object {
        private const val ACCESS_TOKEN = "accessToken"
        private const val REFRESH_TOKEN = "refreshToken"
        private const val NEW_REFRESH_TOKEN = "newRefreshToken"
        private const val COOKIE_HEADER_NAME = "Cookie"
    }
}
