package com.waz.zclient.shared.user.datasources.remote

import com.waz.zclient.UnitTest
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.eq
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

    companion object {
        private const val CANCELLATION_DELAY = 200L
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
    }
}
