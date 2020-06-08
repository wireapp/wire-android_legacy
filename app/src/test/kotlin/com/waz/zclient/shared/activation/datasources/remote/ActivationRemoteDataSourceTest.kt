package com.waz.zclient.shared.activation.datasources.remote

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.network.NetworkHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import retrofit2.Response


@ExperimentalCoroutinesApi
class ActivationRemoteDataSourceTest : UnitTest() {

    private lateinit var activationRemoteDataSource: ActivationRemoteDataSource

    @Mock
    private lateinit var activationApi: ActivationApi

    @Mock
    private lateinit var networkHandler: NetworkHandler

    @Mock
    private lateinit var emptyResponse: Response<Unit>

    @Before
    fun setUp() {
        `when`(networkHandler.isConnected).thenReturn(true)
        activationRemoteDataSource = ActivationRemoteDataSource(activationApi, networkHandler)
    }

    @Test
    fun `Given sendEmailActivationCode() is called, when api response success, then return an success`() =
        coroutinesTestRule.runBlockingTest {

            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)

            `when`(activationApi.sendActivationCode(any())).thenReturn(emptyResponse)

            val response = activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)

            verify(activationApi).sendActivationCode(any())

            assertTrue(response.isRight)
        }

    @Test
    fun `Given sendEmailActivationCode() is called, when api response failed, then return an error`() =
        coroutinesTestRule.runBlockingTest {

            `when`(emptyResponse.isSuccessful).thenReturn(false)
            `when`(activationApi.sendActivationCode(any())).thenReturn(emptyResponse)

            val response = activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)

            verify(activationApi).sendActivationCode(any())

            assertTrue(response.isLeft)
        }

    @Test(expected = CancellationException::class)
    fun `Given sendEmailActivationCode()() is called, when api response is cancelled, then return an error`() =
        runBlocking {

            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(activationApi.sendActivationCode(any())).thenReturn(emptyResponse)

            val response = activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)

            verify(activationApi).sendActivationCode(any())

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)

            assertTrue(response.isLeft)
        }

    @Test
    fun `Given sendPhoneActivationCode() is called, when api response success, then return an success`() =
        coroutinesTestRule.runBlockingTest {

            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)

            `when`(activationApi.sendActivationCode(any())).thenReturn(emptyResponse)

            val response = activationRemoteDataSource.sendPhoneActivationCode(TEST_PHONE)

            verify(activationApi).sendActivationCode(any())

            assertTrue(response.isRight)
        }

    @Test
    fun `Given sendPhoneActivationCode() is called, when api response failed, then return an error`() =
        coroutinesTestRule.runBlockingTest {

            `when`(emptyResponse.isSuccessful).thenReturn(false)
            `when`(activationApi.sendActivationCode(any())).thenReturn(emptyResponse)

            val response = activationRemoteDataSource.sendPhoneActivationCode(TEST_PHONE)

            verify(activationApi).sendActivationCode(any())

            assertTrue(response.isLeft)
        }

    @Test(expected = CancellationException::class)
    fun `Given sendPhoneActivationCode()() is called, when api response is cancelled, then return an error`() =
        runBlocking {

            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(activationApi.sendActivationCode(any())).thenReturn(emptyResponse)

            val response = activationRemoteDataSource.sendPhoneActivationCode(TEST_PHONE)

            verify(activationApi).sendActivationCode(any())

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)

            assertTrue(response.isLeft)
        }

    @Test
    fun `Given activateEmail() is called, when api response success, then return an success`() =
        coroutinesTestRule.runBlockingTest {

            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)

            `when`(activationApi.activate(any())).thenReturn(emptyResponse)

            val response = activationRemoteDataSource.activateEmail(TEST_EMAIL, TEST_CODE)

            verify(activationApi).activate(any())

            assertTrue(response.isRight)
        }

    @Test
    fun `Given activateEmail() is called, when api response failed, then return an error`() =
        coroutinesTestRule.runBlockingTest {

            `when`(emptyResponse.isSuccessful).thenReturn(false)
            `when`(activationApi.activate(any())).thenReturn(emptyResponse)

            val response = activationRemoteDataSource.activateEmail(TEST_EMAIL, TEST_CODE)

            verify(activationApi).activate(any())

            assertTrue(response.isLeft)
        }

    @Test(expected = CancellationException::class)
    fun `Given activateEmail()() is called, when api response is cancelled, then return an error`() =
        runBlocking {

            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(activationApi.activate(any())).thenReturn(emptyResponse)

            val response = activationRemoteDataSource.activateEmail(TEST_EMAIL, TEST_CODE)

            verify(activationApi).activate(any())

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)

            assertTrue(response.isLeft)
        }

    @Test
    fun `Given activatePhone() is called, when api response success, then return an success`() =
        coroutinesTestRule.runBlockingTest {

            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)

            `when`(activationApi.activate(any())).thenReturn(emptyResponse)

            val response = activationRemoteDataSource.activatePhone(TEST_PHONE, TEST_CODE)

            verify(activationApi).activate(any())

            assertTrue(response.isRight)
        }

    @Test
    fun `Given activatePhone() is called, when api response failed, then return an error`() =
        coroutinesTestRule.runBlockingTest {

            `when`(emptyResponse.isSuccessful).thenReturn(false)
            `when`(activationApi.activate(any())).thenReturn(emptyResponse)

            val response = activationRemoteDataSource.activatePhone(TEST_PHONE, TEST_CODE)

            verify(activationApi).activate(any())

            assertTrue(response.isLeft)
        }

    @Test(expected = CancellationException::class)
    fun `Given activatePhone()() is called, when api response is cancelled, then return an error`() =
        runBlocking {

            `when`(emptyResponse.body()).thenReturn(Unit)
            `when`(emptyResponse.isSuccessful).thenReturn(true)
            `when`(activationApi.activate(any())).thenReturn(emptyResponse)

            val response = activationRemoteDataSource.activatePhone(TEST_PHONE, TEST_CODE)

            verify(activationApi).activate(any())

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
            delay(CANCELLATION_DELAY)

            assertTrue(response.isLeft)
        }

    companion object {
        private const val CANCELLATION_DELAY = 200L
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_CODE = "000000"
        private const val TEST_PHONE = "+499999999"
    }
}
