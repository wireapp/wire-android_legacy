package com.waz.zclient.core.network

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.BadRequest
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.Forbidden
import com.waz.zclient.core.exception.InternalServerError
import com.waz.zclient.core.exception.NotFound
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.exception.Unauthorized
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.onFailure
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import retrofit2.Response

//TODO: try to use runBlockingTest once the issue with threading solved:
//https://github.com/Kotlin/kotlinx.coroutines/issues/1222
//https://github.com/Kotlin/kotlinx.coroutines/issues/1204
class ApiServiceTest : UnitTest() {

    private lateinit var apiService: ApiService

    @Mock
    private lateinit var mockNetworkHandler: NetworkHandler

    @Before
    fun setUp() {
        apiService = object : ApiService() {
            override val networkHandler: NetworkHandler = mockNetworkHandler
        }
    }

    @Test
    fun `given call fails with http 400 error, then returns BadRequest failure`() = runBlocking {
        assertHttpError(400, BadRequest)
    }

    @Test
    fun `given call fails with http 401 error, then returns Unauthorized failure`() = runBlocking {
        assertHttpError(401, Unauthorized)
    }

    @Test
    fun `given call fails with http 403 error, then returns Forbidden failure`() = runBlocking {
        assertHttpError(403, Forbidden)
    }

    @Test
    fun `given call fails with http 404 error, then returns NotFound failure`() = runBlocking {
        assertHttpError(404, NotFound)
    }

    @Test
    fun `given call fails with http 500 error, then returns InternalServerError failure`() = runBlocking {
        assertHttpError(500, InternalServerError)
    }

    @Test
    fun `given call fails with any other error, then returns ServerError failure`() = runBlocking {
        assertHttpError(-1, ServerError)
    }

    private suspend fun assertHttpError(httpErrorCode: Int, failure: Failure) {
        `when`(mockNetworkHandler.isConnected).thenReturn(true)

        val response = mock(Response::class.java) as Response<String>
        `when`(response.isSuccessful).thenReturn(false)
        `when`(response.code()).thenReturn(httpErrorCode)
        val responseFunc: suspend () -> Response<String> = { response }

        val result = apiService.request(responseFunc, String.empty())

        result.isLeft shouldBe true
        result.onFailure { it shouldBe failure }
    }
}
