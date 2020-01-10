package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.source.remote.model.UserApi
import kotlinx.coroutines.CancellationException
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import retrofit2.Response

class UserRemoteDataSourceTest {


    private lateinit var usersRemoteDataSource: UsersRemoteDataSource

    @Mock
    private lateinit var usersNetworkService: UsersNetworkService

    @Mock
    private lateinit var response: Response<UserApi>

    @Mock
    private lateinit var userApi: UserApi

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        usersRemoteDataSource = UsersRemoteDataSource(usersNetworkService)
        `when`(response.code()).thenReturn(404)
        `when`(response.message()).thenReturn("Test error message")
    }

    @Test
    fun `Given profile() is called, when api response success and response body is not null, then return a successful response`() {

    }

    @Test
    fun `Given profile() is called, when api response success and response body is null, then return an error response`() {

    }

    @Test(expected = CancellationException::class)
    fun `Given profile() is called, when api response is an error, then return an error response`() {

    }

    @Test
    fun `Given changeName() is called, when api response success, then return a successful response`() {

    }

    @Test(expected = CancellationException::class)
    fun `Given changeName() is called, when api response is an error, then return an error response`() {

    }

    companion object {
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
        private const val TEST_NAME = "name"
    }

}
