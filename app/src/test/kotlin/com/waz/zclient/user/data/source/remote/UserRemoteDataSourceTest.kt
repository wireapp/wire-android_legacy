package com.waz.zclient.user.data.source.remote

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import com.waz.zclient.user.data.source.remote.model.UserApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import retrofit2.Response

@ExperimentalCoroutinesApi
class UserRemoteDataSourceTest : UnitTest() {

    companion object {
        private const val CANCELLATION_DELAY = 200L
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
        private const val TEST_NAME = "name"
        private const val TEST_EMAIL = "email@wire.com"
        private const val TEST_HANDLE = "@handle"
        private const val TEST_PHONE = "+4977738847664"
    }

    private lateinit var usersRemoteDataSource: UsersRemoteDataSource

    @Mock
    private lateinit var usersNetworkService: UsersNetworkService

    @Mock
    private lateinit var userApi: UserApi

    @Mock
    private lateinit var userResponse: Response<UserApi>

    @Mock
    private lateinit var emptyResponse: Response<Unit>

    @Before
    fun setUp() {
        usersRemoteDataSource = UsersRemoteDataSource(usersNetworkService)
    }

    @Test
    fun `Given profileDetails() is called, when api response success and response body is not null, then return a successful response`() {
        runBlockingTest {
            `when`(userResponse.body()).thenReturn(userApi)
            `when`(userResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.profileDetails()).thenReturn(userResponse)

            usersRemoteDataSource.profileDetails()

            verify(usersNetworkService).profileDetails()

            assert(usersRemoteDataSource.profileDetails().isRight)
        }
    }

    @Test
    fun `Given profileDetails() is called, when api response success and response body is null, then return an error response`() {
        runBlockingTest {
            `when`(userResponse.body()).thenReturn(null)
            `when`(userResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.profileDetails()).thenReturn(userResponse)

            usersRemoteDataSource.profileDetails()

            verify(usersNetworkService).profileDetails()

            assert(usersRemoteDataSource.profileDetails().isLeft)
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given profileDetails() is called, when api response is cancelled, then return an error response`() {
        runBlockingTest {
            `when`(userResponse.body()).thenReturn(userApi)
            `when`(userResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.profileDetails()).thenReturn(userResponse)

            usersRemoteDataSource.profileDetails()

            verify(usersNetworkService).profileDetails()

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            assert(usersRemoteDataSource.profileDetails().isLeft)
        }
    }

    @Test
    fun `Given changeName() is called, when api response success and response body is not null, then return a successful response`() {
        runBlockingTest {
            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.changeName(TEST_NAME)).thenReturn(emptyResponse)

            usersRemoteDataSource.changeName(TEST_NAME)

            verify(usersNetworkService).changeName(eq(TEST_NAME))

            assert(usersRemoteDataSource.changeName(TEST_NAME).isRight)
        }
    }

    @Test
    fun `Given changeName() is called, when api response success and response body is null, then return an error response`() {
        runBlockingTest {
            `when`(emptyResponse.body()).thenReturn(null)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.changeName(TEST_NAME)).thenReturn(emptyResponse)

            usersRemoteDataSource.changeName(TEST_NAME)

            verify(usersNetworkService).changeName(eq(TEST_NAME))

            assert(usersRemoteDataSource.changeName(TEST_NAME).isLeft)
        }
    }


    @Test(expected = CancellationException::class)
    fun `Given changeName() is called, when api response is cancelled, then return an error response`() {
        runBlockingTest {
            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.changeName(TEST_NAME)).thenReturn(emptyResponse)

            usersRemoteDataSource.changeName(TEST_NAME)

            verify(usersNetworkService).changeName(TEST_NAME)

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            assert(usersRemoteDataSource.changeName(TEST_NAME).isLeft)
        }
    }

    @Test
    fun `Given changeHandle() is called, when api response success and response body is not null, then return a successful response`() {
        runBlockingTest {
            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.changeHandle(TEST_HANDLE)).thenReturn(emptyResponse)

            usersRemoteDataSource.changeHandle(TEST_HANDLE)

            verify(usersNetworkService).changeHandle(eq(TEST_HANDLE))

            assert(usersRemoteDataSource.changeHandle(TEST_HANDLE).isRight)
        }
    }

    @Test
    fun `Given changeHandle() is called, when api response success and response body is null, then return an error response`() {
        runBlockingTest {
            `when`(emptyResponse.body()).thenReturn(null)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.changeHandle(TEST_HANDLE)).thenReturn(emptyResponse)

            usersRemoteDataSource.changeHandle(TEST_HANDLE)

            verify(usersNetworkService).changeHandle(eq(TEST_HANDLE))

            assert(usersRemoteDataSource.changeHandle(TEST_HANDLE).isLeft)
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given changeHandle() is called, when api response is cancelled, then return an error response`() {
        runBlockingTest {
            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.changeHandle(TEST_HANDLE)).thenReturn(emptyResponse)

            usersRemoteDataSource.changeHandle(TEST_HANDLE)

            verify(usersNetworkService).changeHandle(TEST_HANDLE)

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            assert(usersRemoteDataSource.changeHandle(TEST_HANDLE).isLeft)
        }
    }

    @Test
    fun `Given changeEmail() is called, when api response success and response body is not null, then return a successful response`() {
        runBlockingTest {
            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.changeEmail(TEST_EMAIL)).thenReturn(emptyResponse)

            usersRemoteDataSource.changeEmail(TEST_EMAIL)

            verify(usersNetworkService).changeEmail(eq(TEST_EMAIL))

            assert(usersRemoteDataSource.changeEmail(TEST_EMAIL).isRight)
        }
    }

    @Test
    fun `Given changeEmail() is called, when api response success and response body is null, then return an error response`() {
        runBlockingTest {
            `when`(emptyResponse.body()).thenReturn(null)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.changeEmail(TEST_EMAIL)).thenReturn(emptyResponse)

            usersRemoteDataSource.changeEmail(TEST_EMAIL)

            verify(usersNetworkService).changeEmail(eq(TEST_EMAIL))

            assert(usersRemoteDataSource.changeEmail(TEST_EMAIL).isLeft)
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given changeEmail() is called, when api response is cancelled, then return an error response`() {
        runBlockingTest {
            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.changeEmail(TEST_EMAIL)).thenReturn(emptyResponse)

            usersRemoteDataSource.changeEmail(TEST_EMAIL)

            verify(usersNetworkService).changeEmail(TEST_EMAIL)

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            assert(usersRemoteDataSource.changeEmail(TEST_EMAIL).isLeft)
        }
    }

    @Test
    fun `Given changePhone() is called, when api response success and response body is not null, then return a successful response`() {
        runBlockingTest {
            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.changePhone(TEST_PHONE)).thenReturn(emptyResponse)

            usersRemoteDataSource.changePhone(TEST_PHONE)

            verify(usersNetworkService).changePhone(eq(TEST_PHONE))

            assert(usersRemoteDataSource.changePhone(TEST_PHONE).isRight)
        }
    }

    @Test
    fun `Given changePhone() is called, when api response success and response body is null, then return an error response`() {
        runBlockingTest {
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.changePhone(TEST_PHONE)).thenReturn(emptyResponse)

            usersRemoteDataSource.changePhone(TEST_PHONE)

            verify(usersNetworkService).changePhone(eq(TEST_PHONE))

            assert(usersRemoteDataSource.changePhone(TEST_PHONE).isLeft)
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given changePhone() is called, when api response is cancelled, then return an error response`() {
        runBlockingTest {
            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(usersNetworkService.changePhone(TEST_PHONE)).thenReturn(emptyResponse)

            usersRemoteDataSource.changePhone(TEST_PHONE)

            verify(usersNetworkService).changePhone(TEST_PHONE)

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(CANCELLATION_DELAY)

            assert(usersRemoteDataSource.changePhone(TEST_PHONE).isLeft)
        }
    }


}
