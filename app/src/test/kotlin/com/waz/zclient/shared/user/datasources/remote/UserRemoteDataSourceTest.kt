package com.waz.zclient.shared.user.datasources.remote

import com.waz.zclient.UnitTest
import com.waz.zclient.capture
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.eq
import com.waz.zclient.shared.user.handle.HandleAlreadyExists
import com.waz.zclient.shared.user.handle.HandleInvalid
import com.waz.zclient.shared.user.handle.HandleIsAvailable
import com.waz.zclient.shared.user.handle.UnknownError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBe
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
    private lateinit var usersApi: UsersApi

    @Mock
    private lateinit var networkHandler: NetworkHandler

    @Mock
    private lateinit var userResponse: UserResponse

    @Mock
    private lateinit var httpUserResponse: Response<UserResponse>

    @Mock
    private lateinit var httpEmptyResponse: Response<Unit>

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
        usersRemoteDataSource = UsersRemoteDataSource(usersApi, networkHandler)
    }

    @Test
    fun `Given profileDetails() is called, when api response success and response body is not null, then return a successful response`() {
        validateProfileDetailsScenario(responseBody = userResponse, isRight = true, cancelable = false)
    }

    @Test
    fun `Given profileDetails() is called, when api response success and response body is null, then return an error response`() {
        validateProfileDetailsScenario(responseBody = null, isRight = false, cancelable = false)
    }

    @Test(expected = CancellationException::class)
    fun `Given profileDetails() is called, when api response is cancelled, then return an error response`() {
        validateProfileDetailsScenario(responseBody = userResponse, isRight = false, cancelable = true)
    }

    private fun validateProfileDetailsScenario(responseBody: UserResponse?, isRight: Boolean, cancelable: Boolean) = runBlocking {
        `when`(httpUserResponse.body()).thenReturn(responseBody)
        `when`(httpUserResponse.isSuccessful).thenReturn(true)
        `when`(usersApi.profileDetails()).thenReturn(httpUserResponse)

        usersRemoteDataSource.profileDetails()

        verify(usersApi).profileDetails()

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
        `when`(httpEmptyResponse.body()).thenReturn(responseBody)
        `when`(httpEmptyResponse.isSuccessful).thenReturn(true)
        `when`(usersApi.changeName(capture(changeNameRequestCaptor))).thenReturn(httpEmptyResponse)

        usersRemoteDataSource.changeName(TEST_NAME)

        verify(usersApi).changeName(capture(changeNameRequestCaptor))

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
        `when`(httpEmptyResponse.body()).thenReturn(responseBody)
        `when`(httpEmptyResponse.isSuccessful).thenReturn(true)
        `when`(usersApi.changeHandle(capture(changeHandleRequestCaptor))).thenReturn(httpEmptyResponse)

        usersRemoteDataSource.changeHandle(TEST_HANDLE)

        verify(usersApi).changeHandle(capture(changeHandleRequestCaptor))

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
        `when`(httpEmptyResponse.body()).thenReturn(responseBody)
        `when`(httpEmptyResponse.isSuccessful).thenReturn(true)
        `when`(usersApi.changeEmail(capture(changeEmailRequestCaptor))).thenReturn(httpEmptyResponse)

        usersRemoteDataSource.changeEmail(TEST_EMAIL)

        verify(usersApi).changeEmail(capture(changeEmailRequestCaptor))

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
        `when`(httpEmptyResponse.body()).thenReturn(responseBody)
        `when`(httpEmptyResponse.isSuccessful).thenReturn(true)
        `when`(usersApi.changePhone(capture(changePhoneRequestCaptor))).thenReturn(httpEmptyResponse)

        usersRemoteDataSource.changePhone(TEST_PHONE)

        verify(usersApi).changePhone(capture(changePhoneRequestCaptor))

        if (cancelable) {
            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)
        }

        changePhoneRequestCaptor.value.phone shouldBe TEST_PHONE

        usersRemoteDataSource.changePhone(TEST_PHONE).isRight shouldBe isRight
    }

    @Test
    fun `Given doesHandleExist() is called, when response code is 200, then return a failure`() {
        `when`(httpEmptyResponse.code()).thenReturn(HANDLE_TAKEN)
        validateHandleExistsFailure(failure = HandleAlreadyExists)

    }

    @Test
    fun `Given doesHandleExist() is called, when response code is 400, then return a failure`() {
        `when`(httpEmptyResponse.code()).thenReturn(HANDLE_INVALID)
        validateHandleExistsFailure(failure = HandleInvalid)

    }

    @Test
    fun `Given doesHandleExist() is called, when response code is 404, then return a HandleIsAvailable success`() {
        `when`(httpEmptyResponse.code()).thenReturn(HANDLE_AVAILABLE)
        validateHandleExistsSuccess()
    }

    @Test
    fun `Given doesHandleExist() is called, when response code is not 200, 400 or 404, then return a failure`() {
        `when`(httpEmptyResponse.code()).thenReturn(HANDLE_UNKNOWN)
        validateHandleExistsFailure(failure = UnknownError)
    }

    @Test(expected = CancellationException::class)
    fun `Given doesHandleExist() is called, and the request is cancelled, then return a failure`() {
        validateHandleExistsFailure(cancelled = true, failure = UnknownError)
    }

    private fun validateHandleExistsSuccess() = runBlocking {
        `when`(usersApi.doesHandleExist(TEST_HANDLE)).thenReturn(httpEmptyResponse)

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE)

        verify(usersApi).doesHandleExist(eq(TEST_HANDLE))

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE).isRight shouldBe true

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE).onSuccess {
            it shouldBe HandleIsAvailable
        }
    }


    private fun validateHandleExistsFailure(cancelled: Boolean = false, failure: Failure) = runBlocking {
        `when`(usersApi.doesHandleExist(TEST_HANDLE)).thenReturn(httpEmptyResponse)

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE)

        verify(usersApi).doesHandleExist(eq(TEST_HANDLE))

        if (cancelled) {
            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)
        }

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE).isLeft shouldBe true

        usersRemoteDataSource.doesHandleExist(TEST_HANDLE).onFailure {
            it shouldBe failure
        }
    }

    @Test
    fun `Given deletePhone() is called, when api response success and response body is not null, then return a successful response`() {
        validateDeletePhoneScenario(responseBody = Unit, isRight = true, cancelable = false)
    }

    @Test
    fun `Given deletePhone() is called, when api response success and response body is null, then return an error response`() {
        validateDeletePhoneScenario(responseBody = null, isRight = false, cancelable = false)
    }

    @Test(expected = CancellationException::class)
    fun `Given deletePhone() is called, when api response is cancelled, then return an error response`() {
        validateDeletePhoneScenario(responseBody = Unit, isRight = false, cancelable = true)
    }

    private fun validateDeletePhoneScenario(responseBody: Unit?, isRight: Boolean, cancelable: Boolean) = runBlocking {
        `when`(httpEmptyResponse.body()).thenReturn(responseBody)
        `when`(httpEmptyResponse.isSuccessful).thenReturn(true)
        `when`(usersApi.deletePhone()).thenReturn(httpEmptyResponse)

        usersRemoteDataSource.deletePhone()

        verify(usersApi).deletePhone()

        if (cancelable) {
            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)
        }

        usersRemoteDataSource.deletePhone().isRight shouldBe isRight
    }

    @Test
    fun `Given deleteAccountPermanently() is called, when api response success and response body is not null, then return a successful response`() {
        validateDeleteAccountPermanantlyScenario(responseBody = Unit, isRight = true, cancelable = false)
    }

    @Test
    fun `Given deleteAccountPermanently() is called, when api response success and response body is null, then return an error response`() {
        validateDeleteAccountPermanantlyScenario(responseBody = null, isRight = false, cancelable = false)
    }

    @Test(expected = CancellationException::class)
    fun `Given deleteAccountPermanently() is called, when api response is cancelled, then return an error response`() {
        validateDeleteAccountPermanantlyScenario(responseBody = Unit, isRight = false, cancelable = true)
    }

    private fun validateDeleteAccountPermanantlyScenario(responseBody: Unit?, isRight: Boolean, cancelable: Boolean) = runBlocking {
        `when`(httpEmptyResponse.body()).thenReturn(responseBody)
        `when`(httpEmptyResponse.isSuccessful).thenReturn(true)
        `when`(usersApi.deleteAccount(DeleteAccountRequest)).thenReturn(httpEmptyResponse)

        usersRemoteDataSource.deleteAccountPermanently()

        verify(usersApi).deleteAccount(DeleteAccountRequest)

        if (cancelable) {
            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)
        }

        usersRemoteDataSource.deleteAccountPermanently().isRight shouldBe isRight
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
