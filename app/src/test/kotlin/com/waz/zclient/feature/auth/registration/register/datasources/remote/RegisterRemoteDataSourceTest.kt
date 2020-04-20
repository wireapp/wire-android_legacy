package com.waz.zclient.feature.auth.registration.register.datasources.remote

import com.waz.zclient.UnitTest
import com.waz.zclient.capture
import com.waz.zclient.core.network.NetworkHandler
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
class RegisterRemoteDataSourceTest : UnitTest() {

    private lateinit var registerRemoteDataSource: RegisterRemoteDataSource

    @Mock
    private lateinit var registerApi: RegisterApi

    @Mock
    private lateinit var networkHandler: NetworkHandler

    @Mock
    private lateinit var response: Response<UserResponse>

    @Mock
    private lateinit var userResponse: UserResponse

    @Captor
    private lateinit var registerRequestBodyCapture: ArgumentCaptor<RegisterRequestBody>

    @Before
    fun setUp() {
        `when`(networkHandler.isConnected).thenReturn(true)
        registerRemoteDataSource = RegisterRemoteDataSource(registerApi, networkHandler)
    }

    @Test
    fun `Given registerPersonalAccountWithEmail() is called, when api response success, then return an success`() = runBlocking {

        `when`(response.body()).thenReturn(userResponse)
        `when`(response.isSuccessful).thenReturn(true)

        `when`(registerApi.register(capture(registerRequestBodyCapture))).thenReturn(response)

        val response = registerRemoteDataSource.registerPersonalAccountWithEmail(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_ACTIVATION_CODE)

        verify(registerApi).register(capture(registerRequestBodyCapture))

        registerRequestBodyCapture.value.name shouldBe TEST_NAME
        registerRequestBodyCapture.value.email shouldBe TEST_EMAIL
        registerRequestBodyCapture.value.password shouldBe TEST_PASSWORD
        registerRequestBodyCapture.value.emailCode shouldBe TEST_ACTIVATION_CODE

        response.isRight shouldBe true
    }

    @Test
    fun `Given registerPersonalAccountWithEmail() is called, when api response failed, then return an error`() = runBlocking {

        `when`(response.isSuccessful).thenReturn(false)
        `when`(registerApi.register(capture(registerRequestBodyCapture))).thenReturn(response)

        val response = registerRemoteDataSource.registerPersonalAccountWithEmail(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_ACTIVATION_CODE)

        verify(registerApi).register(capture(registerRequestBodyCapture))

        registerRequestBodyCapture.value.name shouldBe TEST_NAME
        registerRequestBodyCapture.value.email shouldBe TEST_EMAIL
        registerRequestBodyCapture.value.password shouldBe TEST_PASSWORD
        registerRequestBodyCapture.value.emailCode shouldBe TEST_ACTIVATION_CODE

        response.isLeft shouldBe true
    }

    @Test(expected = CancellationException::class)
    fun `Given  registerPersonalAccountWithEmail() is called, when api response is cancelled, then return an error`() = runBlocking {

        `when`(response.body()).thenReturn(userResponse)
        `when`(response.isSuccessful).thenReturn(true)
        `when`(registerApi.register(capture(registerRequestBodyCapture))).thenReturn(response)

        val response = registerRemoteDataSource.registerPersonalAccountWithEmail(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_ACTIVATION_CODE)

        verify(registerApi).register(capture(registerRequestBodyCapture))

        cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
        delay(CANCELLATION_DELAY)

        registerRequestBodyCapture.value.name shouldBe TEST_NAME
        registerRequestBodyCapture.value.email shouldBe TEST_EMAIL
        registerRequestBodyCapture.value.password shouldBe TEST_PASSWORD
        registerRequestBodyCapture.value.emailCode shouldBe TEST_ACTIVATION_CODE

        response.isLeft shouldBe true
    }

    companion object {
        private const val CANCELLATION_DELAY = 200L
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
        private const val TEST_NAME = "testName"
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_PASSWORD = "testPass"
        private const val TEST_ACTIVATION_CODE = "000000"
    }
}
