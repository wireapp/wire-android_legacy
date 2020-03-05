package com.waz.zclient.auth.registration.activation

import com.waz.zclient.UnitTest
import com.waz.zclient.capture
import com.waz.zclient.core.network.NetworkHandler
import kotlinx.coroutines.*
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
class ActivationRemoteDataSourceTest : UnitTest() {

    private lateinit var activationRemoteDataSource: ActivationRemoteDataSource

    @Mock
    private lateinit var activationApi: ActivationApi

    @Mock
    private lateinit var networkHandler: NetworkHandler

    @Mock
    private lateinit var emptyResponse: Response<Unit>

    @Captor
    private lateinit var sendEmailActivationCodeRequestCaptor: ArgumentCaptor<SendActivationCodeRequest>

    @Before
    fun setUp() {
        `when`(networkHandler.isConnected).thenReturn(true)
        activationRemoteDataSource = ActivationRemoteDataSource(activationApi, networkHandler)
    }

    @Test
    fun `Given sendActivationCode() is called, when api response success, then return an success`() = runBlocking {

        `when`(emptyResponse.body()).thenReturn(Unit)
        `when`(emptyResponse.isSuccessful).thenReturn(true)
        `when`(activationApi.sendActivationCode(capture(sendEmailActivationCodeRequestCaptor))).thenReturn(emptyResponse)

        val response = activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)

        verify(activationApi).sendActivationCode(capture(sendEmailActivationCodeRequestCaptor))

        sendEmailActivationCodeRequestCaptor.value.email shouldBe TEST_EMAIL

        response.isRight shouldBe true
    }

    @Test
    fun `Given sendActivationCode() is called, when api response failed, then return an error`() = runBlocking {

        `when`(emptyResponse.isSuccessful).thenReturn(false)
        `when`(activationApi.sendActivationCode(capture(sendEmailActivationCodeRequestCaptor))).thenReturn(emptyResponse)

        val response = activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)

        verify(activationApi).sendActivationCode(capture(sendEmailActivationCodeRequestCaptor))

        sendEmailActivationCodeRequestCaptor.value.email shouldBe TEST_EMAIL

        response.isLeft shouldBe true
    }

    @Test(expected = CancellationException::class)
    fun `Given  sendActivationCode()() is called, when api response is cancelled, then return an error`() = runBlocking {

        `when`(emptyResponse.body()).thenReturn(Unit)
        `when`(emptyResponse.isSuccessful).thenReturn(true)
        `when`(activationApi.sendActivationCode(capture(sendEmailActivationCodeRequestCaptor))).thenReturn(emptyResponse)

        val response = activationRemoteDataSource.sendEmailActivationCode(TEST_EMAIL)

        verify(activationApi).sendActivationCode(capture(sendEmailActivationCodeRequestCaptor))

        cancel(CancellationException(TEST_EXCEPTION_MESSAGE))
        delay(CANCELLATION_DELAY)

        sendEmailActivationCodeRequestCaptor.value.email shouldBe TEST_EMAIL

        response.isLeft shouldBe true
    }

    companion object {
        private const val CANCELLATION_DELAY = 200L
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
        private const val TEST_EMAIL = "test@wire.com"
    }
}
