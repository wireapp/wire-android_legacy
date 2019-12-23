package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.*
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.threading.ThreadHandler
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.*
import retrofit2.Call
import retrofit2.Response

class ApiServiceTest : UnitTest() {

    private lateinit var apiService: ApiService

    @Mock
    private lateinit var networkHandler: NetworkHandler

    @Mock
    private lateinit var threadHandler: ThreadHandler

    @Mock
    private lateinit var networkClient: NetworkClient

    @Before
    fun setUp() {
        apiService = ApiService(networkHandler, threadHandler, networkClient)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given thread is UI thread, when requesting a call, throws exception`() {
        `when`(threadHandler.isUIThread()).thenReturn(true)
        val (call, _) = mockResponse<String>()
        apiService.request(call, String.empty())
    }

    @Test
    fun `given a network call, when requesting a call, checks the current thread as first step`() {
        `when`(threadHandler.isUIThread()).thenReturn(false)
        `when`(networkHandler.isConnected).thenReturn(true)

        val (call, _) = mockResponse<String>()
        apiService.request(call, String.empty())

        val inOrder = inOrder(threadHandler, networkHandler)
        inOrder.verify(threadHandler).isUIThread()
        inOrder.verify(networkHandler).isConnected
    }

    @Test
    fun `given call fails with http 400 error, then returns BadRequest failure`() =
        assertHttpError(400, BadRequest)

    @Test
    fun `given call fails with http 401 error, then returns Unauthorized failure`() =
        assertHttpError(401, Unauthorized)

    @Test
    fun `given call fails with http 403 error, then returns Forbidden failure`() =
        assertHttpError(403, Forbidden)

    @Test
    fun `given call fails with http 404 error, then returns NotFound failure`() =
        assertHttpError(404, NotFound)

    @Test
    fun `given call fails with http 500 error, then returns InternalServerError failure`() =
        assertHttpError(500, InternalServerError)

    @Test
    fun `given call fails with any other error, then returns ServerError failure`() =
        assertHttpError(-1, ServerError)

    private fun assertHttpError(httpErrorCode: Int, failure: Failure) {
        `when`(networkHandler.isConnected).thenReturn(true)
        `when`(threadHandler.isUIThread()).thenReturn(false)

        val (call, response) = mockResponse<String>()
        `when`(response.isSuccessful).thenReturn(false)
        `when`(response.code()).thenReturn(httpErrorCode)

        val result = apiService.request(call, String.empty())

        result.isLeft shouldBe true
        result.onFailure { it shouldBe failure }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> mockResponse(): Pair<Call<T>, Response<T>> {
        val call: Call<T> = mock(Call::class.java) as Call<T>
        val response: Response<T> = mock(Response::class.java) as Response<T>
        `when`(call.execute()).thenReturn(response)

        return call to response
    }
}
