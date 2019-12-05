package com.waz.client.user.data.source.remote

import com.waz.client.NetworkSecurityPolicyShadow
import com.waz.client.util.anyParam
import com.waz.zclient.user.data.source.remote.AUTH_HEADER_TEXT
import com.waz.zclient.user.data.source.remote.AuthHeaderInterceptor
import com.waz.zclient.user.data.source.remote.AuthRetryDelegate
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [NetworkSecurityPolicyShadow::class])
class AuthHeaderInterceptorTest {

    @Mock
    private lateinit var authRetryDelegate: AuthRetryDelegate

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        mockWebServer = MockWebServer()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `when token is set, adds it to authentication header`() {
        mockWebServer.enqueue(MockResponse())
        mockWebServer.start()

        //given
        val interceptor = AuthHeaderInterceptor(authRetryDelegate)

        val testTokenType = "testTokenType"
        val testToken = "testToken"
        interceptor.run {
            tokenType = testTokenType
            token = testToken
        }

        //when
        val request = Request.Builder().url(mockWebServer.url("/")).build()
        val okHttpClient = OkHttpClient().newBuilder().addInterceptor(interceptor).build()
        okHttpClient.newCall(request).execute()

        //then
        val recordedRequest = mockWebServer.takeRequest()
        Assert.assertEquals(
            recordedRequest.headers[AUTH_HEADER_TEXT],
            "$testTokenType $testToken"
        )
    }

    @Test
    fun `when token is not set, does not add authentication header`() {
        mockWebServer.enqueue(MockResponse())
        mockWebServer.start()

        //given
        val interceptor = AuthHeaderInterceptor(authRetryDelegate)

        interceptor.token = null

        //when
        val request = Request.Builder().url(mockWebServer.url("/")).build()
        val okHttpClient = OkHttpClient().newBuilder().addInterceptor(interceptor).build()
        okHttpClient.newCall(request).execute()

        //then
        val recordedRequest = mockWebServer.takeRequest()
        Assert.assertEquals(
            recordedRequest.headers[AUTH_HEADER_TEXT],
            null
        )
    }

    @Test
    fun `when Authorization error is received, starts retry mechanism`() {
        mockWebServer.enqueue(MockResponse()) //initial response
        mockWebServer.enqueue(MockResponse()) //retry response
        mockWebServer.start()

        //given
        val interceptor = AuthHeaderInterceptor(authRetryDelegate)

        `when`(authRetryDelegate.retryRequired(anyParam())).thenReturn(true, false)

        //when
        val request = Request.Builder().url(mockWebServer.url("/")).build()
        val okHttpClient = OkHttpClient().newBuilder().addInterceptor(interceptor).build()
        okHttpClient.newCall(request).execute()

        //then
        mockWebServer.takeRequest()
        verify(authRetryDelegate, times(1)).startRetryProcess()
    }

    @Test
    fun `when original response is successful, does not start retry mechanism`() {
        mockWebServer.enqueue(MockResponse())
        mockWebServer.start()

        //given
        val interceptor = AuthHeaderInterceptor(authRetryDelegate)

        `when`(authRetryDelegate.retryRequired(anyParam())).thenReturn(false)

        //when
        val request = Request.Builder().url(mockWebServer.url("/")).build()
        val okHttpClient = OkHttpClient().newBuilder().addInterceptor(interceptor).build()
        okHttpClient.newCall(request).execute()

        //then
        mockWebServer.takeRequest()
        verify(authRetryDelegate, never()).startRetryProcess()

    }
}
