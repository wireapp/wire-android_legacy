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
    private lateinit var profileResponse: Response<UserApi>

    @Mock
    private lateinit var userApi: UserApi

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        usersRemoteDataSource = UsersRemoteDataSource(usersNetworkService)
        `when`(profileResponse.code()).thenReturn(404)
        `when`(profileResponse.message()).thenReturn("Test error message")
    }

    @Test
    fun `Given profile() is called, when api response success and response body is not null, then return a successful response`() {
        runBlocking {

            `when`(profileResponse.body()).thenReturn(userApi)
            `when`(profileResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.profile()).thenReturn(profileResponse)

            usersRemoteDataSource.profile()

            verify(usersNetworkService).profile()

            assert(usersRemoteDataSource.profile().isRight)
        }
    }

    @Test
    fun `Given profile() is called, when api response success and response body is null, then return an error response`() {
        runBlocking {
            `when`(profileResponse.body()).thenReturn(null)
            `when`(profileResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.profile()).thenReturn(profileResponse)

            usersRemoteDataSource.profile()

            verify(usersNetworkService).profile()

            assert(usersRemoteDataSource.profile().isLeft)
        }

    }

    @Test(expected = CancellationException::class)
    fun `Given profile() is called, when api response is an error, then return an error response`() {
        runBlocking {
            `when`(usersNetworkService.profile()).thenReturn(profileResponse)

            usersRemoteDataSource.profile()

            verify(usersNetworkService).profile()

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            assert(usersRemoteDataSource.profile().isLeft)
        }
    }

    companion object {
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
    }

}
