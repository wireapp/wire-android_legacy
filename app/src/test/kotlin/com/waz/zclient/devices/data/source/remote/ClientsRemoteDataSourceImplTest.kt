package com.waz.zclient.devices.data.source.remote

import com.waz.zclient.devices.data.model.ClientEntity
import com.waz.zclient.framework.mockito.eq
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import retrofit2.Response

class ClientsRemoteDataSourceImplTest {

    private lateinit var remoteDataSource: ClientsRemoteDataSource

    @Mock
    private lateinit var clientsApi: ClientsApi

    @Mock
    private lateinit var allClientsResponse: Response<Array<ClientEntity>>

    @Mock
    private lateinit var clientByIdResponse: Response<ClientEntity>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        remoteDataSource = ClientsRemoteDataSourceImpl(clientsApi)
    }

    @Test
    fun `Given getAllClients() is called, when api response success, then return a successful response`() {
        runBlocking {
            val clientEntity = mock(ClientEntity::class.java)
            `when`(allClientsResponse.body()).thenReturn(arrayOf(clientEntity))
            `when`(allClientsResponse.isSuccessful).thenReturn(true)
            `when`(clientsApi.allClientsAsync()).thenReturn(allClientsResponse)

            remoteDataSource.allClients()

            verify(clientsApi).allClientsAsync()

            assert(remoteDataSource.allClients().isRight)
        }
    }

    @Test
    fun `Given getAllClients() is called, when api response success and response body is null, then return an error response`() {
        runBlocking {
            `when`(allClientsResponse.body()).thenReturn(null)
            `when`(allClientsResponse.isSuccessful).thenReturn(true)
            `when`(clientsApi.allClientsAsync()).thenReturn(allClientsResponse)

            remoteDataSource.allClients()

            verify(clientsApi).allClientsAsync()

            assert(remoteDataSource.allClients().isLeft)
        }

    }

    @Test(expected = CancellationException::class)
    fun `Given getAllClients() is called, when api response is an error, then return an error response`() {
        runBlocking {
            `when`(clientsApi.allClientsAsync()).thenReturn(allClientsResponse)

            remoteDataSource.allClients()

            verify(clientsApi).allClientsAsync()

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            assert(remoteDataSource.allClients().isLeft)
        }
    }

    @Test
    fun `Given getClientById() is called, when api response success, then return a successful response`() {
        runBlocking {
            val clientEntity = mock(ClientEntity::class.java)
            `when`(clientByIdResponse.body()).thenReturn(clientEntity)
            `when`(clientByIdResponse.isSuccessful).thenReturn(true)
            `when`(clientsApi.clientByIdAsync(TEST_ID)).thenReturn(clientByIdResponse)

            remoteDataSource.clientById(TEST_ID)

            verify(clientsApi).clientByIdAsync(eq(TEST_ID))

            assert(remoteDataSource.clientById(TEST_ID).isRight)
        }
    }

    @Test
    fun `Given getClientById() is called, when api response success and response body is null, then return an error response`() {
        runBlocking {
            `when`(clientByIdResponse.body()).thenReturn(null)
            `when`(clientByIdResponse.isSuccessful).thenReturn(true)
            `when`(clientsApi.clientByIdAsync(TEST_ID)).thenReturn(clientByIdResponse)

            remoteDataSource.clientById(TEST_ID)

            verify(clientsApi).clientByIdAsync(eq(TEST_ID))

            assert(remoteDataSource.clientById(TEST_ID).isLeft)
        }

    }

    @Test(expected = CancellationException::class)
    fun `Given getClientById() is called, when api response is an error, then return an error response`() {
        runBlocking {
            `when`(clientsApi.clientByIdAsync(TEST_ID)).thenReturn(clientByIdResponse)

            remoteDataSource.clientById(TEST_ID)

            verify(clientsApi).clientByIdAsync(eq(TEST_ID))

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            assert(remoteDataSource.clientById(TEST_ID).isLeft)
        }
    }


    companion object {
        private const val TEST_ID = "Test Id"
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
    }
}
