package com.waz.zclient.user.data.source.remote

import com.waz.zclient.UnitTest
import com.waz.zclient.capture
import com.waz.zclient.eq
import com.waz.zclient.user.data.source.remote.model.UserApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
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
        private const val HANDLE_TAKEN = 200
        private const val HANDLE_INVALID = 400
        private const val HANDLE_AVAILABLE = 404
        private const val HANDLE_UNKNOWN = 500
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

    @Captor
    private lateinit var changeEmailRequestCaptor: ArgumentCaptor<ChangeEmailRequest>

    @Captor
    private lateinit var changePhoneRequestCaptor: ArgumentCaptor<ChangePhoneRequest>

    @Captor
    private lateinit var changeHandleRequestCaptor: ArgumentCaptor<ChangeHandleRequest>

    @Captor
    private lateinit var changeNameRequestCaptor: ArgumentCaptor<ChangeNameRequest>

    @Before
    fun setUp() {
        usersRemoteDataSource = UsersRemoteDataSource(usersNetworkService)
    }

    @Test
    fun `Given profileDetails() is called, when api response success and response body is not null, then return a successful response`() {
        validateProfileDetailsScenario(responseBody = userApi, isRight = true, cancelable = false)
    }

    @Test
    fun `Given profileDetails() is called, when api response success and response body is null, then return an error response`() {
        validateProfileDetailsScenario(responseBody = null, isRight = false, cancelable = false)
    }

    @Test(expected = CancellationException::class)
    fun `Given profileDetails() is called, when api response is cancelled, then return an error response`() {
        validateProfileDetailsScenario(responseBody = userApi, isRight = false, cancelable = true)
    }

    private fun validateProfileDetailsScenario(responseBody: UserApi?, isRight: Boolean, cancelable: Boolean) = runBlockingTest {
        `when`(userResponse.body()).thenReturn(responseBody)
        `when`(userResponse.isSuccessful).thenReturn(true)
        `when`(usersNetworkService.profileDetails()).thenReturn(userResponse)

        usersRemoteDataSource.profileDetails()

        verify(usersNetworkService).profileDetails()

        if (cancelable) {
            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)
        }

        usersRemoteDataSource.profileDetails().isRight shouldBe isRight
    }

    @Test
    fun `Given changeName() is called, when api response success and response body is not null, then return a successful response`() {
        validateChangeNameScenario(responseBody = Unit, isRight = true, cancelable = false)
    }

    @Test
    fun `Given changeName() is called, when api response success and response body is null, then return an error response`() {
        validateChangeNameScenario(responseBody = null, isRight = false, cancelable = false)
    }

    @Test(expected = CancellationException::class)
    fun `Given changeName() is called, when api response is cancelled, then return an error response`() {
        validateChangeNameScenario(responseBody = Unit, isRight = false, cancelable = true)
    }

    private fun validateChangeNameScenario(responseBody: Unit?, isRight: Boolean, cancelable: Boolean) = runBlockingTest {
        `when`(emptyResponse.body()).thenReturn(responseBody)
        `when`(emptyResponse.isSuccessful).thenReturn(true)
        `when`(usersNetworkService.changeName(capture(changeNameRequestCaptor))).thenReturn(emptyResponse)

        usersRemoteDataSource.changeName(TEST_NAME)

        verify(usersNetworkService).changeName(capture(changeNameRequestCaptor))

        if (cancelable) {
            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)
        }

        changeNameRequestCaptor.value.name shouldBe TEST_NAME

        usersRemoteDataSource.changeName(TEST_NAME).isRight shouldBe isRight
    }

    @Test
    fun `Given changeHandle() is called, when api response success and response body is not null, then return a successful response`() {
        validateChangeHandleScenario(responseBody = Unit, isRight = true, cancelable = false)
    }

    @Test
    fun `Given changeHandle() is called, when api response success and response body is null, then return an error response`() {
        validateChangeHandleScenario(responseBody = null, isRight = false, cancelable = false)
    }

    @Test(expected = CancellationException::class)
    fun `Given changeHandle() is called, when api response is cancelled, then return an error response`() {
        validateChangeHandleScenario(responseBody = Unit, isRight = false, cancelable = true)
    }

    private fun validateChangeHandleScenario(responseBody: Unit?, isRight: Boolean, cancelable: Boolean) = runBlockingTest {
        `when`(emptyResponse.body()).thenReturn(responseBody)
        `when`(emptyResponse.isSuccessful).thenReturn(true)
        `when`(usersNetworkService.changeHandle(capture(changeHandleRequestCaptor))).thenReturn(emptyResponse)

        usersRemoteDataSource.changeHandle(TEST_HANDLE)

        verify(usersNetworkService).changeHandle(capture(changeHandleRequestCaptor))

        if (cancelable) {
            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)
        }

        changeHandleRequestCaptor.value.handle shouldBe TEST_HANDLE

        usersRemoteDataSource.changeHandle(TEST_HANDLE).isRight shouldBe isRight
    }

    @Test
    fun `Given changeEmail() is called, when api response success and response body is not null, then return a successful response`() {
        validateChangeEmailScenario(responseBody = Unit, isRight = true, cancelable = false)
    }

    @Test
    fun `Given changeEmail() is called, when api response success and response body is null, then return an error response`() {
        validateChangeEmailScenario(responseBody = null, isRight = false, cancelable = false)
    }

    @Test(expected = CancellationException::class)
    fun `Given changeEmail() is called, when api response is cancelled, then return an error response`() {
        validateChangeEmailScenario(responseBody = Unit, isRight = false, cancelable = true)
    }

    private fun validateChangeEmailScenario(responseBody: Unit?, isRight: Boolean, cancelable: Boolean) = runBlockingTest {
        `when`(emptyResponse.body()).thenReturn(responseBody)
        `when`(emptyResponse.isSuccessful).thenReturn(true)
        `when`(usersNetworkService.changeEmail(capture(changeEmailRequestCaptor))).thenReturn(emptyResponse)

        usersRemoteDataSource.changeEmail(TEST_EMAIL)

        verify(usersNetworkService).changeEmail(capture(changeEmailRequestCaptor))

        if (cancelable) {
            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)
        }

        changeEmailRequestCaptor.value.email shouldBe TEST_EMAIL

        usersRemoteDataSource.changeEmail(TEST_EMAIL).isRight shouldBe isRight
    }


    @Test
    fun `Given changePhone() is called, when api response success and response body is not null, then return a successful response`() {
        validateChangePhoneScenario(responseBody = Unit, isRight = true, cancelable = false)

    }

    @Test
    fun `Given changePhone() is called, when api response success and response body is null, then return an error response`() {
        validateChangePhoneScenario(responseBody = null, isRight = false, cancelable = false)

    }

    @Test(expected = CancellationException::class)
    fun `Given changePhone() is called, when api response is cancelled, then return an error response`() {
        validateChangePhoneScenario(responseBody = Unit, isRight = false, cancelable = true)
    }

    private fun validateChangePhoneScenario(responseBody: Unit?, isRight: Boolean, cancelable: Boolean) = runBlockingTest {
        `when`(emptyResponse.body()).thenReturn(responseBody)
        `when`(emptyResponse.isSuccessful).thenReturn(true)
        `when`(usersNetworkService.changePhone(capture(changePhoneRequestCaptor))).thenReturn(emptyResponse)

        usersRemoteDataSource.changePhone(TEST_PHONE)

        verify(usersNetworkService).changePhone(capture(changePhoneRequestCaptor))

        if (cancelable) {
            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)
        }

        changePhoneRequestCaptor.value.phone shouldBe TEST_PHONE

        usersRemoteDataSource.changePhone(TEST_PHONE).isRight shouldBe isRight
    }

    @Test
    fun `Given doesHandleExist() is called, when response code is 200, then return a failure`() {
        `when`(emptyResponse.code()).thenReturn(HANDLE_TAKEN)
        validateHandleExists()

    }

    @Test
    fun `Given doesHandleExist() is called, when response code is 400, then return a failure`() {
        `when`(emptyResponse.code()).thenReturn(HANDLE_INVALID)
        validateHandleExists()

    }

    @Test
    fun `Given doesHandleExist() is called, when response code is 404, then return a HandleIsAvailable success`() {
        `when`(emptyResponse.code()).thenReturn(HANDLE_AVAILABLE)
        validateHandleExists(isError = false)
    }

    @Test
    fun `Given doesHandleExist() is called, when response code is not 200, 400 or 404, then return a failure`() {
        `when`(emptyResponse.code()).thenReturn(HANDLE_UNKNOWN)
        validateHandleExists()
    }

    private fun validateHandleExists(isError: Boolean = true) = runBlockingTest {
        `when`(usersNetworkService.doesHandleExist(TEST_HANDLE)).thenReturn(emptyResponse)

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE)

        verify(usersNetworkService).doesHandleExist(eq(TEST_HANDLE))

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE).isLeft shouldBe isError
    }
}
