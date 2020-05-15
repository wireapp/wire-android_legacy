package com.waz.zclient.core.network.accesstoken

import com.waz.zclient.UnitTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
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

@ExperimentalCoroutinesApi
class AccessTokenInterceptorTest : UnitTest() {

    @Mock
    lateinit var repository: AccessTokenRepository

    private lateinit var accessTokenInterceptor: AccessTokenInterceptor

    @Before
    fun setUp() {
        accessTokenInterceptor = AccessTokenInterceptor(repository)
    }

    @Test
    fun `if there's an AccessToken, adds its token to request's header`() {
        runBlockingTest { `when`(repository.accessToken()).thenReturn(ACCESS_TOKEN) }

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

        runBlockingTest { verify(repository).accessToken() }
        verify(requestBuilder).addHeader("Authorization", "Bearer ${ACCESS_TOKEN.token}")
        verify(chain).proceed(requestWithHeader)
        verify(requestBuilder, never()).removeHeader("Authorization")
    }

    @Test
    fun `if AccessToken is empty, doesn't add authorization header`() {
        val chain = mock(Interceptor.Chain::class.java)
        val request = mock(Request::class.java)
        runBlockingTest { `when`(repository.accessToken()).thenReturn(AccessToken.EMPTY) }
        `when`(chain.request()).thenReturn(request)
        `when`(chain.proceed(request)).thenReturn(mock(Response::class.java))

        accessTokenInterceptor.intercept(chain)

        verify(chain).proceed(request)
        //also verify that there's no attempt to create a new request
        verifyNoMoreInteractions(request)
    }

    companion object {
        private val ACCESS_TOKEN = AccessToken("accessToken", "type", "expiresIn")
    }
}
