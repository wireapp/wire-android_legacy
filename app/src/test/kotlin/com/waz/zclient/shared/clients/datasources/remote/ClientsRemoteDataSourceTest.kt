package com.waz.zclient.shared.clients.datasources.remote

import com.waz.zclient.core.network.NetworkHandler
import com.waz.zclient.eq
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import retrofit2.Response

class ClientsRemoteDataSourceTest {

    private lateinit var remoteDataSource: ClientsRemoteDataSource

    @Mock
    private lateinit var clientsApi: ClientsApi

    @Mock
    private lateinit var networkHandler: NetworkHandler

    @Mock
    private lateinit var allClientsResponse: Response<List<ClientResponse>>

    @Mock
    private lateinit var clientByIdResponse: Response<ClientResponse>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        remoteDataSource = ClientsRemoteDataSource(networkHandler, clientsApi)
        `when`(networkHandler.isConnected).thenReturn(true)
        `when`(allClientsResponse.code()).thenReturn(TEST_NETWORK_ERROR_CODE)
        `when`(clientByIdResponse.code()).thenReturn(TEST_NETWORK_ERROR_CODE)
        `when`(clientByIdResponse.message()).thenReturn(TEST_NETWORK_ERROR_MESSAGE)
        `when`(allClientsResponse.message()).thenReturn(TEST_NETWORK_ERROR_MESSAGE)

    }

    @Test
    fun `Given allClients() is called, when api response success, then return a successful response`() {
        runBlocking {
            val clientResponse = mock(ClientResponse::class.java)
            `when`(allClientsResponse.body()).thenReturn(listOf(clientResponse))
            `when`(allClientsResponse.isSuccessful).thenReturn(true)
            `when`(clientsApi.allClients()).thenReturn(allClientsResponse)

            remoteDataSource.allClients()

            verify(clientsApi).allClients()

            assert(remoteDataSource.allClients().isRight)
        }
    }

    @Test
    fun `Given allClients() is called, when api response is success and body is null, then return an error response`() {
        runBlocking {
            `when`(allClientsResponse.body()).thenReturn(null)
            `when`(allClientsResponse.isSuccessful).thenReturn(true)
            `when`(clientsApi.allClients()).thenReturn(allClientsResponse)

            remoteDataSource.allClients()

            verify(clientsApi).allClients()

            assert(remoteDataSource.allClients().isLeft)
        }

    }

    @Test(expected = CancellationException::class)
    fun `Given allClients() is called, when api response is an error, then return an error response`() {
        runBlocking {
            `when`(clientsApi.allClients()).thenReturn(allClientsResponse)

            remoteDataSource.allClients()

            verify(clientsApi).allClients()

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            assert(remoteDataSource.allClients().isLeft)
        }
    }

    @Test
    fun `Given clientById() is called, when api response success, then return a successful response`() {
        runBlocking {
            val clientEntity = mock(ClientResponse::class.java)
            `when`(clientByIdResponse.body()).thenReturn(clientEntity)
            `when`(clientByIdResponse.isSuccessful).thenReturn(true)
            `when`(clientsApi.clientById(TEST_ID)).thenReturn(clientByIdResponse)

            remoteDataSource.clientById(TEST_ID)

            verify(clientsApi).clientById(eq(TEST_ID))

            assert(remoteDataSource.clientById(TEST_ID).isRight)
        }
    }

    @Test
    fun `Given clientById() is called, when api response is success and body is null, then return an error response`() {
        runBlocking {
            `when`(clientByIdResponse.body()).thenReturn(null)
            `when`(clientByIdResponse.isSuccessful).thenReturn(true)
            `when`(clientsApi.clientById(TEST_ID)).thenReturn(clientByIdResponse)

            remoteDataSource.clientById(TEST_ID)

            verify(clientsApi).clientById(eq(TEST_ID))

            assert(remoteDataSource.clientById(TEST_ID).isLeft)
        }

    }

    @Test(expected = CancellationException::class)
    fun `Given clientById() is called, when api response is an error, then return an error response`() {
        runBlocking {
            `when`(clientsApi.clientById(TEST_ID)).thenReturn(clientByIdResponse)

            remoteDataSource.clientById(TEST_ID)

            verify(clientsApi).clientById(eq(TEST_ID))

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            assert(remoteDataSource.clientById(TEST_ID).isLeft)
        }
    }


    companion object {
        private const val TEST_NETWORK_ERROR_CODE = 404
        private const val TEST_NETWORK_ERROR_MESSAGE = "Network request failed"
        private const val TEST_ID = "Test Id"
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
    }
}
