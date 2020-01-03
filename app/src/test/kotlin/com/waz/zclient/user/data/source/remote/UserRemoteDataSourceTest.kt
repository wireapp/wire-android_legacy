package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.source.remote.model.UserApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
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
        runBlocking {

            `when`(response.body()).thenReturn(userApi)
            `when`(response.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.profile()).thenReturn(response)

            usersRemoteDataSource.profile()

            verify(usersNetworkService).profile()

            assert(usersRemoteDataSource.profile().isRight)
        }
    }

    @Test
    fun `Given profile() is called, when api response success and response body is null, then return an error response`() {
        runBlocking {
            `when`(response.body()).thenReturn(null)
            `when`(response.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.profile()).thenReturn(response)

            usersRemoteDataSource.profile()

            verify(usersNetworkService).profile()

            assert(usersRemoteDataSource.profile().isLeft)
        }

    }

    @Test(expected = CancellationException::class)
    fun `Given profile() is called, when api response is an error, then return an error response`() {
        runBlocking {
            `when`(usersNetworkService.profile()).thenReturn(response)

            usersRemoteDataSource.profile()

            verify(usersNetworkService).profile()

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            assert(usersRemoteDataSource.profile().isLeft)
        }
    }

    @Test
    fun `Given changeName() is called, when api response success, then return a successful response`() {
        runBlocking {

            `when`(response.body()).thenReturn(UserApi())
            `when`(response.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.changeName(UserApi(name = TEST_NAME))).thenReturn(response)

            usersRemoteDataSource.changeName(TEST_NAME)

            verify(usersNetworkService).changeName(UserApi(name = TEST_NAME))

            assert(usersRemoteDataSource.changeName(TEST_NAME).isRight)

        }
    }

    @Test(expected = CancellationException::class)
    fun `Given changeName() is called, when api response is an error, then return an error response`() {
        runBlocking {

            `when`(response.isSuccessful).thenReturn(false)
            `when`(usersNetworkService.changeName(UserApi(name = TEST_NAME))).thenReturn(response)

            usersRemoteDataSource.changeName(TEST_NAME)

            verify(usersNetworkService).changeName(UserApi(name = TEST_NAME))

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            assert(usersRemoteDataSource.changeName(TEST_NAME).isLeft)
        }
    }

    companion object {
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
        private const val TEST_NAME = "name"
    }

}
