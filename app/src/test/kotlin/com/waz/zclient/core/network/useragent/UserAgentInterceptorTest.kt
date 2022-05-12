package com.waz.zclient.core.network.useragent

import com.waz.zclient.UnitTest
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*

class UserAgentInterceptorTest : UnitTest() {

    private lateinit var userAgentInterceptor: UserAgentInterceptor

    @Mock
    private lateinit var chain: Interceptor.Chain

    @Before
    fun setup() {
        userAgentInterceptor = UserAgentInterceptor()
        `when`(chain.proceed(any())).thenReturn(mock(Response::class.java))
    }

    @Test
    fun `given a chain, when request does not have a User-Agent header, then proceeds with request`() {
        val initialRequest = mock(Request::class.java)
        `when`(chain.request()).thenReturn(initialRequest)
        `when`(initialRequest.header(USER_AGENT_HEADER_KEY)).thenReturn(null)

        userAgentInterceptor.intercept(chain)

        verify(chain).proceed(initialRequest)
        verify(initialRequest, never()).newBuilder()
    }

    @Test
    fun `given a chain, when request has a User-Agent header, then removes the header`() {
        val initialRequest = mock(Request::class.java)
        `when`(chain.request()).thenReturn(initialRequest)
        `when`(initialRequest.header(USER_AGENT_HEADER_KEY)).thenReturn(HEADER_VALUE)

        val requestBuilder = mock(Request.Builder::class.java)
        `when`(initialRequest.newBuilder()).thenReturn(requestBuilder)
        `when`(requestBuilder.removeHeader(any())).thenReturn(requestBuilder)

        val request = mock(Request::class.java)
        `when`(requestBuilder.build()).thenReturn(request)

        userAgentInterceptor.intercept(chain)

        verify(chain).proceed(request)
        verify(requestBuilder).removeHeader(USER_AGENT_HEADER_KEY)
    }

    companion object {
        private const val USER_AGENT_HEADER_KEY = "User-Agent"
        private const val HEADER_VALUE = "header value"
    }
}
