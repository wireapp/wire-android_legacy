package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import com.waz.zclient.core.extension.empty
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
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

    @Before
    fun setUp() {
        accessTokenInterceptor = AccessTokenInterceptor(authTokenHandler)
    }

    @Test
    fun `if there's an access token, adds it to request's header`() {
        `when`(authTokenHandler.accessToken()).thenReturn("testToken")

        val chain = mock(Interceptor.Chain::class.java)
        val initialRequest = mock(Request::class.java)
        `when`(chain.request()).thenReturn(initialRequest)

        val requestBuilder = mock(Request.Builder::class.java)
        `when`(initialRequest.newBuilder()).thenReturn(requestBuilder)
        `when`(requestBuilder.addHeader(any(), any())).thenReturn(requestBuilder)

        val requestWithHeader = mock(Request::class.java)
        `when`(requestBuilder.build()).thenReturn(requestWithHeader)

        `when`(chain.proceed(requestWithHeader)).thenReturn(mock(Response::class.java))

        accessTokenInterceptor.intercept(chain)

        verify(authTokenHandler).accessToken()
        verify(requestBuilder).addHeader("Authorization", "Bearer testToken")
        verify(chain).proceed(requestWithHeader)
        verify(requestBuilder, never()).removeHeader("Authorization")
    }

    @Test
    fun `if access token is empty, doesn't add authorization header`() {
        val chain = mock(Interceptor.Chain::class.java)
        val request = mock(Request::class.java)
        `when`(authTokenHandler.accessToken()).thenReturn(String.empty())
        `when`(chain.request()).thenReturn(request)
        `when`(chain.proceed(request)).thenReturn(mock(Response::class.java))

        accessTokenInterceptor.intercept(chain)

        verify(chain).proceed(request)
        //also verify that there's no attempt to create a new request
        verifyNoMoreInteractions(request)
    }

}
