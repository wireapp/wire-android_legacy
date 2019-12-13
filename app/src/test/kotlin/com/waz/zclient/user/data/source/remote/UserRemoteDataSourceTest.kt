package com.waz.zclient.user.data.source.remote

import com.waz.zclient.storage.db.model.UserEntity
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
    private lateinit var usersApi: UsersApi

    @Mock
    private lateinit var profileResponse: Response<UserEntity>

    @Mock
    private lateinit var userEntity: UserEntity

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        usersRemoteDataSource = UsersRemoteDataSource(usersApi)
        `when`(profileResponse.code()).thenReturn(404)
        `when`(profileResponse.message()).thenReturn("Test error message")
    }

    @Test
    fun `Given profile() is called, when api response success and response body is not null, then return a successful response`() {
        runBlocking {

            `when`(profileResponse.body()).thenReturn(userEntity)
            `when`(profileResponse.isSuccessful).thenReturn(true)
            `when`(usersApi.profile()).thenReturn(profileResponse)

            usersRemoteDataSource.profile()

            verify(usersApi).profile()

            assert(usersRemoteDataSource.profile().isRight)
        }
    }

    @Test
    fun `Given profile() is called, when api response success and response body is null, then return an error response`() {
        runBlocking {
            `when`(profileResponse.body()).thenReturn(null)
            `when`(profileResponse.isSuccessful).thenReturn(true)
            `when`(usersApi.profile()).thenReturn(profileResponse)

            usersRemoteDataSource.profile()

            verify(usersApi).profile()

            assert(usersRemoteDataSource.profile().isLeft)
        }

    }

    @Test(expected = CancellationException::class)
    fun `Given profile() is called, when api response is an error, then return an error response`() {
        runBlocking {
            `when`(usersApi.profile()).thenReturn(profileResponse)

            usersRemoteDataSource.profile()

            verify(usersApi).profile()

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            assert(usersRemoteDataSource.profile().isLeft)
        }
    }

    companion object {
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
    }

}
