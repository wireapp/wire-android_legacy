package com.waz.zclient.feature.auth.registration.register.datasources.remote

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.capture
import com.waz.zclient.core.network.NetworkHandler
import junit.framework.TestCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `Given registerPersonalAccountWithEmail() is called, when api response success, then return an success`() =
        runBlocking {

            `when`(response.body()).thenReturn(userResponse)
            `when`(response.isSuccessful).thenReturn(true)

            `when`(registerApi.register(any())).thenReturn(response)

            val response = registerRemoteDataSource.registerPersonalAccountWithEmail(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_ACTIVATION_CODE)

            verify(registerApi).register(capture(registerRequestBodyCapture))

            assertEquals(TEST_NAME, registerRequestBodyCapture.value.name)
            assertEquals(TEST_EMAIL, registerRequestBodyCapture.value.email)
            assertEquals(TEST_PASSWORD, registerRequestBodyCapture.value.password)
            assertEquals(TEST_ACTIVATION_CODE, registerRequestBodyCapture.value.emailCode)
            assertTrue(response.isRight)
        }

    @Test
    fun `Given registerPersonalAccountWithEmail() is called, when api response failed, then return an error`() =
        runBlocking {

            `when`(response.isSuccessful).thenReturn(false)
            `when`(registerApi.register(any())).thenReturn(response)

            val response = registerRemoteDataSource.registerPersonalAccountWithEmail(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_ACTIVATION_CODE)

            verify(registerApi).register(capture(registerRequestBodyCapture))

            assertEquals(TEST_NAME, registerRequestBodyCapture.value.name)
            assertEquals(TEST_EMAIL, registerRequestBodyCapture.value.email)
            assertEquals(TEST_PASSWORD, registerRequestBodyCapture.value.password)
            assertEquals(TEST_ACTIVATION_CODE, registerRequestBodyCapture.value.emailCode)
            assertTrue(response.isLeft)

        }

    @Test(expected = CancellationException::class)
    fun `Given  registerPersonalAccountWithEmail() is called, when api response is cancelled, then return an error`() =
        runBlocking {

            `when`(response.body()).thenReturn(userResponse)
            `when`(response.isSuccessful).thenReturn(true)
            `when`(registerApi.register(any())).thenReturn(response)

            val response = registerRemoteDataSource.registerPersonalAccountWithEmail(TEST_NAME, TEST_EMAIL, TEST_PASSWORD, TEST_ACTIVATION_CODE)

            verify(registerApi).register(capture(registerRequestBodyCapture))

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)

            assertEquals(TEST_NAME, registerRequestBodyCapture.value.name)
            assertEquals(TEST_EMAIL, registerRequestBodyCapture.value.email)
            assertEquals(TEST_PASSWORD, registerRequestBodyCapture.value.password)
            assertEquals(TEST_ACTIVATION_CODE, registerRequestBodyCapture.value.emailCode)
            assertTrue(response.isLeft)
        }

    @Test
    fun `Given registerPersonalAccountWithPhone() is called, when api response success, then return an success`() =
        runBlocking {

            `when`(response.body()).thenReturn(userResponse)
            `when`(response.isSuccessful).thenReturn(true)

            `when`(registerApi.register(any())).thenReturn(response)

            val response = registerRemoteDataSource.registerPersonalAccountWithPhone(TEST_NAME, TEST_PHONE, TEST_ACTIVATION_CODE)

            verify(registerApi).register(capture(registerRequestBodyCapture))

            assertEquals(TEST_NAME, registerRequestBodyCapture.value.name)
            assertEquals(TEST_PHONE, registerRequestBodyCapture.value.phone)
            assertEquals(TEST_ACTIVATION_CODE, registerRequestBodyCapture.value.phoneCode)
            assertTrue(response.isRight)
        }

    @Test
    fun `Given registerPersonalAccountWithPhone() is called, when api response failed, then return an error`() =
        runBlocking {

            `when`(response.isSuccessful).thenReturn(false)
            `when`(registerApi.register(any())).thenReturn(response)

            val response = registerRemoteDataSource.registerPersonalAccountWithPhone(TEST_NAME, TEST_PHONE, TEST_ACTIVATION_CODE)

            verify(registerApi).register(capture(registerRequestBodyCapture))

            assertEquals(TEST_NAME, registerRequestBodyCapture.value.name)
            assertEquals(TEST_PHONE, registerRequestBodyCapture.value.phone)
            assertEquals(TEST_ACTIVATION_CODE, registerRequestBodyCapture.value.phoneCode)
            assertTrue(response.isLeft)

        }

    @Test(expected = CancellationException::class)
    fun `Given  registerPersonalAccountWithPhone() is called, when api response is cancelled, then return an error`() =
        runBlocking {

            `when`(response.body()).thenReturn(userResponse)
            `when`(response.isSuccessful).thenReturn(true)
            `when`(registerApi.register(any())).thenReturn(response)

            val response = registerRemoteDataSource.registerPersonalAccountWithPhone(TEST_NAME, TEST_PHONE, TEST_ACTIVATION_CODE)

            verify(registerApi).register(capture(registerRequestBodyCapture))

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)

            assertEquals(TEST_NAME, registerRequestBodyCapture.value.name)
            assertEquals(TEST_PHONE, registerRequestBodyCapture.value.phone)
            assertEquals(TEST_ACTIVATION_CODE, registerRequestBodyCapture.value.phoneCode)
            assertTrue(response.isLeft)
        }

    companion object {
        private const val CANCELLATION_DELAY = 200L
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
        private const val TEST_NAME = "testName"
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_PASSWORD = "testPass"
        private const val TEST_ACTIVATION_CODE = "000000"
        private const val TEST_PHONE = "+499999999"
    }
}
