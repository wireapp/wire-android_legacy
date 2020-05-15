package com.waz.zclient.shared.activation.datasources.remote

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.shared.activation.datasources.remote.ActivationApi
import com.waz.zclient.shared.activation.datasources.remote.ActivationRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBe
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
    fun `Given sendActivationCode() is called, when api response success, then return an success`() = runBlocking {

        `when`(emptyResponse.body()).thenReturn(Unit)
        `when`(emptyResponse.isSuccessful).thenReturn(true)

        `when`(activationApi.sendActivationCode(any())).thenReturn(emptyResponse)

        val response = activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)

        verify(activationApi).sendActivationCode(any())

        response.isRight shouldBe true
    }

    @Test
    fun `Given sendActivationCode() is called, when api response failed, then return an error`() = runBlocking {

        `when`(emptyResponse.isSuccessful).thenReturn(false)
        `when`(activationApi.sendActivationCode(any())).thenReturn(emptyResponse)

        val response = activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)

        verify(activationApi).sendActivationCode(any())

        response.isLeft shouldBe true
    }

    @Test(expected = CancellationException::class)
    fun `Given  sendActivationCode()() is called, when api response is cancelled, then return an error`() = runBlocking {

        `when`(emptyResponse.body()).thenReturn(Unit)
        `when`(emptyResponse.isSuccessful).thenReturn(true)
        `when`(activationApi.sendActivationCode(any())).thenReturn(emptyResponse)

        val response = activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)

        verify(activationApi).sendActivationCode(any())

        cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
        delay(CANCELLATION_DELAY)

        response.isLeft shouldBe true
    }

    @Test
    fun `Given activateEmail() is called, when api response success, then return an success`() = runBlocking {

        `when`(emptyResponse.body()).thenReturn(Unit)
        `when`(emptyResponse.isSuccessful).thenReturn(true)

        `when`(activationApi.activate(any())).thenReturn(emptyResponse)

        val response = activationRemoteDataSource.activateEmail(TEST_EMAIL, TEST_CODE)

        verify(activationApi).activate(any())

        response.isRight shouldBe true
    }

    @Test
    fun `Given activateEmail() is called, when api response failed, then return an error`() = runBlocking {

        `when`(emptyResponse.isSuccessful).thenReturn(false)
        `when`(activationApi.activate(any())).thenReturn(emptyResponse)

        val response = activationRemoteDataSource.activateEmail(TEST_EMAIL, TEST_CODE)

        verify(activationApi).activate(any())

        response.isLeft shouldBe true
    }

    @Test(expected = CancellationException::class)
    fun `Given  activateEmail()() is called, when api response is cancelled, then return an error`() = runBlocking {

        `when`(emptyResponse.body()).thenReturn(Unit)
        `when`(emptyResponse.isSuccessful).thenReturn(true)
        `when`(activationApi.activate(any())).thenReturn(emptyResponse)

        val response = activationRemoteDataSource.activateEmail(TEST_EMAIL, TEST_CODE)

        verify(activationApi).activate(any())

        cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
        delay(CANCELLATION_DELAY)

        response.isLeft shouldBe true
    }

    companion object {
        private const val CANCELLATION_DELAY = 200L
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
        private const val TEST_EMAIL = "test@wire.com"
        private const val TEST_CODE = "000000"
    }
}
