package com.waz.zclient.core.network.useragent

import com.waz.zclient.UnitTest
import com.waz.zclient.core.config.AppVersionNameConfig
import com.waz.zclient.core.extension.empty
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class UserAgentInterceptorTest : UnitTest() {

    private lateinit var userAgentInterceptor: UserAgentInterceptor

    @Mock
    private lateinit var userAgentConfig: UserAgentConfig

    @Mock
    private lateinit var appVersionNameConfig: AppVersionNameConfig

    @Before
    fun setup() {
        userAgentInterceptor = UserAgentInterceptor(userAgentConfig)
        `when`(userAgentConfig.androidVersion).thenReturn(ANDROID_VERSION)
        `when`(userAgentConfig.appVersionNameConfig).thenReturn(appVersionNameConfig)
        `when`(userAgentConfig.httpUserAgent).thenReturn(HTTP_LIBRARY_VERSION)

        `when`(appVersionNameConfig.versionName).thenReturn(WIRE_VERSION)
    }


    @Test
    fun `Given HttpRequest header User-Agent does not exist already, then add new header to request with user-agent details`() {
        val chain = mock(Interceptor.Chain::class.java)
        val initialRequest = mock(Request::class.java)
        `when`(chain.request()).thenReturn(initialRequest)

        val requestBuilder = mock(Request.Builder::class.java)
        `when`(initialRequest.newBuilder()).thenReturn(requestBuilder)
        `when`(requestBuilder.addHeader(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(requestBuilder)

        val requestWithHeader = mock(Request::class.java)
        requestBuilder.addHeader(USER_AGENT_HEADER_KEY, String.empty())
        `when`(requestBuilder.build()).thenReturn(requestWithHeader)
        `when`(chain.proceed(requestWithHeader)).thenReturn(mock(Response::class.java))

        userAgentInterceptor.intercept(chain)

        verify(requestBuilder).addHeader(USER_AGENT_HEADER_KEY, userAgent)
        verify(chain).proceed(requestWithHeader)
        verify(requestBuilder, never()).removeHeader(USER_AGENT_HEADER_KEY)
    }

    @Test
    fun `given current User-Agent header does exist already, then proceed with initial request`() {
        val chain = mock(Interceptor.Chain::class.java)
        val initialRequest = mock(Request::class.java)
        `when`(chain.request()).thenReturn(initialRequest)

        val requestBuilder = mock(Request.Builder::class.java)
        `when`(initialRequest.newBuilder()).thenReturn(requestBuilder)
        `when`(requestBuilder.addHeader(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(requestBuilder)

        val requestWithHeader = mock(Request::class.java)
        requestBuilder.addHeader(USER_AGENT_HEADER_KEY, userAgent)
        `when`(requestBuilder.build()).thenReturn(requestWithHeader)
        `when`(chain.proceed(requestWithHeader)).thenReturn(mock(Response::class.java))

        userAgentInterceptor.intercept(chain)

        verify(chain).proceed(requestWithHeader)
        verify(requestBuilder, never()).removeHeader(USER_AGENT_HEADER_KEY)
    }

    companion object {
        private const val USER_AGENT_HEADER_KEY = "User-Agent"
        private const val ANDROID_VERSION = "10.0"
        private const val WIRE_VERSION = "3.12.300"
        private const val HTTP_LIBRARY_VERSION = "4.1.0"
        private val userAgent = """Android $ANDROID_VERSION 
           / Wire $WIRE_VERSION 
           / HttpLibrary $HTTP_LIBRARY_VERSION
        """.trimIndent()
    }
}
