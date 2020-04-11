package com.waz.zclient.core.network.backend

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.capture
import com.waz.zclient.core.backend.BackendRepository
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

class CustomBackendInterceptorTest : UnitTest() {

    @Mock
    private lateinit var backendRepository: BackendRepository

    @Mock
    private lateinit var chain: Interceptor.Chain

    @Mock
    private lateinit var response: Response

    @Captor
    private lateinit var requestCaptor: ArgumentCaptor<Request>

    private lateinit var customBackendInterceptor: CustomBackendInterceptor

    @Before
    fun setUp() {
        `when`(chain.proceed(any())).thenReturn(response)

        customBackendInterceptor = CustomBackendInterceptor(backendRepository)
    }

    @Test
    fun `given backendRepository has a configuredUrl, when intercept is called, changes the request's base url`() {
        val newBaseUrl = "https://www.newHost.com"
        `when`(backendRepository.configuredUrl()).thenReturn(newBaseUrl)
        val request = buildRequest()
        `when`(chain.request()).thenReturn(request)

        customBackendInterceptor.intercept(chain)

        verify(chain).proceed(capture(requestCaptor))

        val newUrl = requestCaptor.value.url().toString()
        assert(newUrl.equals("$newBaseUrl/$REQUEST_PATH", ignoreCase = true))
    }

    @Test
    fun `given backendRepository doesn't have a configuredUrl, when intercept is called, does not change request's url`() {
        `when`(backendRepository.configuredUrl()).thenReturn(null)
        val request = buildRequest()
        `when`(chain.request()).thenReturn(request)

        customBackendInterceptor.intercept(chain)

        verify(chain).proceed(capture(requestCaptor))
        requestCaptor.value shouldBe request
    }

    companion object {
        private const val REQUEST_PATH = "some/path/here"
        private fun buildRequest() = Request.Builder().url("http://www.oldHost.com/$REQUEST_PATH").build()
    }
}
