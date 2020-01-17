package com.waz.zclient.user.data.source.remote

import com.waz.zclient.UnitTest
import com.waz.zclient.capture
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.eq
import com.waz.zclient.user.data.source.remote.model.UserApi
import com.waz.zclient.user.domain.usecase.handle.HandleExistsAlreadyError
import com.waz.zclient.user.domain.usecase.handle.HandleInvalidError
import com.waz.zclient.user.domain.usecase.handle.HandleIsAvailable
import com.waz.zclient.user.domain.usecase.handle.HandleUnknownError
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import retrofit2.Response

//TODO: try to use runBlockingTest once the issue with threading solved:
//https://github.com/Kotlin/kotlinx.coroutines/issues/1222
//https://github.com/Kotlin/kotlinx.coroutines/issues/1204
@ExperimentalCoroutinesApi
class UserRemoteDataSourceTest : UnitTest() {

    private lateinit var usersRemoteDataSource: UsersRemoteDataSource

    @Mock
    private lateinit var usersNetworkService: UsersNetworkService

    @Mock
    private lateinit var networkHandler: NetworkHandler

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
        `when`(networkHandler.isConnected).thenReturn(true)
        usersRemoteDataSource = UsersRemoteDataSource(usersNetworkService, networkHandler)
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

    private fun validateProfileDetailsScenario(responseBody: UserApi?, isRight: Boolean, cancelable: Boolean) = runBlocking {
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

    private fun validateChangeNameScenario(responseBody: Unit?, isRight: Boolean, cancelable: Boolean) = runBlocking {
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

    private fun validateChangeHandleScenario(responseBody: Unit?, isRight: Boolean, cancelable: Boolean) = runBlocking {
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

    private fun validateChangeEmailScenario(responseBody: Unit?, isRight: Boolean, cancelable: Boolean) = runBlocking {
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

    private fun validateChangePhoneScenario(responseBody: Unit?, isRight: Boolean, cancelable: Boolean) = runBlocking {
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
        validateHandleExistsFailure(errorClass = HandleExistsAlreadyError::class.java)

    }

    @Test
    fun `Given doesHandleExist() is called, when response code is 400, then return a failure`() {
        `when`(emptyResponse.code()).thenReturn(HANDLE_INVALID)
        validateHandleExistsFailure(errorClass = HandleInvalidError::class.java)

    }

    @Test
    fun `Given doesHandleExist() is called, when response code is 404, then return a HandleIsAvailable success`() {
        `when`(emptyResponse.code()).thenReturn(HANDLE_AVAILABLE)
        validateHandleExistsSuccess()
    }

    @Test
    fun `Given doesHandleExist() is called, when response code is not 200, 400 or 404, then return a failure`() {
        `when`(emptyResponse.code()).thenReturn(HANDLE_UNKNOWN)
        validateHandleExistsFailure(errorClass = HandleUnknownError::class.java)
    }

    @Test(expected = CancellationException::class)
    fun `Given doesHandleExist() is called, and the request is cancelled, then return a failure`() {
        validateHandleExistsFailure(cancelled = true, errorClass = HandleUnknownError::class.java)
    }

    private fun validateHandleExistsSuccess() = runBlockingTest {
        `when`(usersNetworkService.doesHandleExist(TEST_HANDLE)).thenReturn(emptyResponse)

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE)

        verify(usersNetworkService).doesHandleExist(eq(TEST_HANDLE))

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE).isRight shouldBe true

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE).onSuccess {
            it shouldBeInstanceOf HandleIsAvailable::class.java
        }
    }


    private fun validateHandleExistsFailure(cancelled: Boolean = false, errorClass: Class<*>) = runBlockingTest {
        `when`(usersNetworkService.doesHandleExist(TEST_HANDLE)).thenReturn(emptyResponse)

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE)

        verify(usersNetworkService).doesHandleExist(eq(TEST_HANDLE))

        if (cancelled) {
            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)
        }

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE).isLeft shouldBe true

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE).onFailure {
            it shouldBeInstanceOf errorClass
        }
    }

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
}
