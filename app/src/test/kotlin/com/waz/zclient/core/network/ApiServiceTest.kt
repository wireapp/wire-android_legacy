package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.BadRequest
import com.waz.zclient.core.exception.Unauthorized
import com.waz.zclient.core.threading.ThreadHandler
import com.waz.zclient.testutils.verifyLeft
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoMoreInteractions
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

    @Test
    fun `given thread is UI thread, when requesting a call, fails fast before executing the call`() {
        doAnswer { throw UIThreadException }.`when`(threadHandler).failFastIfUIThread()
        val (call, response) = prepareResponse<String>()

        //TODO: is there a better way to verify this exception? :'(
        try {
            apiService.request(call, "")
        } catch (e: Exception) {
            e shouldBe UIThreadException
            verifyNoMoreInteractions(networkHandler, call, response)
        }

    }

    //region http error code matching

    @Test
    fun `given call fails with http 400 error, then returns BadRequest failure`() {
        `when`(networkHandler.isConnected).thenReturn(true)
        doNothing().`when`(threadHandler).failFastIfUIThread()

        val (call, _) = prepareResponseError<String>(400)

        val result = apiService.request(call, "")

        result.isLeft shouldBe true
        result verifyLeft BadRequest
    }

    @Test
    fun `given call fails with http 401 error, then returns Unauthorized failure`() {
        `when`(networkHandler.isConnected).thenReturn(true)
        doNothing().`when`(threadHandler).failFastIfUIThread()

        val (call, _) = prepareResponseError<String>(401)

        val result = apiService.request(call, "")

        result.isLeft shouldBe true
        result verifyLeft Unauthorized
    }

    //endregion


    private fun <T> prepareResponse(): Pair<Call<T>, Response<T>> {
        val call: Call<T> = mock(Call::class.java) as Call<T>
        val response: Response<T> = mock(Response::class.java) as Response<T>
        `when`(call.execute()).thenReturn(response)
        return call to response
    }

    private fun <T> prepareResponseError(httpErrorCode: Int): Pair<Call<T>, Response<T>> {
        val (call, response) = prepareResponse<T>()
        `when`(response.isSuccessful).thenReturn(false)
        `when`(response.code()).thenReturn(httpErrorCode)
        return call to response
    }

}

object UIThreadException : Exception()
