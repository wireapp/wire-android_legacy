package com.waz.zclient.devices.data

import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.exception.HttpError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.devices.data.source.ClientMapper
import com.waz.zclient.devices.data.source.local.ClientsLocalDataSource
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.devices.data.source.remote.model.ClientApi
import com.waz.zclient.eq
import com.waz.zclient.storage.db.clients.model.ClientDao
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class ClientsRepositoryTest {

    private lateinit var repository: ClientsRepository

    @Mock
    private lateinit var remoteDataSource: ClientsRemoteDataSource

    @Mock
    private lateinit var localDataSource: ClientsLocalDataSource

    @Mock
    private lateinit var clientMapper: ClientMapper

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        repository = ClientsDataSource(remoteDataSource, localDataSource, clientMapper)
    }

    @Test
    fun `Given getAllClients() is called, when the local data source succeeded, then map the data response to domain`() {
        runBlocking {
            `when`(localDataSource.allClients()).thenReturn(Either.Right(listOf(generateMockDao())))

            repository.allClients()

            verify(localDataSource).allClients()

            localDataSource.allClients().map {
                verify(clientMapper).toListOfClients(it)
            }
        }
    }


    @Test
    fun `Given getAllClients() is called, when the local data source failed, remote data source is called, then map the data response to domain`() {
        runBlocking {
            `when`(remoteDataSource.allClients()).thenReturn(Either.Right(listOf(generateMockApi())))
            `when`(localDataSource.allClients()).thenReturn(Either.Left(DatabaseError))

            repository.allClients()

            verify(remoteDataSource).allClients()

            remoteDataSource.allClients().map {
                verify(clientMapper).toListOfClients(it)
            }
        }
    }

    @Test
    fun `Given allClients() is called, when the local data source failed, remote data source is called and failed, then return error`() {
        runBlocking {
            `when`(remoteDataSource.allClients()).thenReturn(Either.Left(HttpError(TEST_CODE, TEST_MESSAGE)))
            `when`(localDataSource.allClients()).thenReturn(Either.Left(DatabaseError))

            repository.allClients()

            verify(remoteDataSource).allClients()

            assert(repository.allClients().isLeft)
        }
    }

    @Test
    fun `Given getClientById() is called, when the local data source succeeded, then map the data response to domain`() {
        runBlocking {
            `when`(localDataSource.clientById(TEST_ID)).thenReturn(Either.Right(generateMockDao()))

            repository.clientById(TEST_ID)

            verify(localDataSource).clientById(eq(TEST_ID))

            localDataSource.clientById(TEST_ID).map {
                verify(clientMapper).toClient(it)
            }
        }
    }

    @Test
    fun `Given getClientById() is called, when the local data source failed, remote data source is called, then map the data response to domain`() {
        runBlocking {
            `when`(remoteDataSource.clientById(TEST_ID)).thenReturn(Either.Right(generateMockApi()))
            `when`(localDataSource.clientById(TEST_ID)).thenReturn(Either.Left(DatabaseError))

            repository.clientById(TEST_ID)

            verify(remoteDataSource).clientById(eq(TEST_ID))

            remoteDataSource.clientById(TEST_ID).map { clientApi ->
                verify(clientMapper).toClient(clientApi)
            }
        }
    }

    @Test
    fun `Given getClientById() is called, when the local data source failed, remote data source is called and failed, then return error`() {
        runBlocking {
            `when`(remoteDataSource.clientById(TEST_ID)).thenReturn(Either.Left(HttpError(TEST_CODE, TEST_MESSAGE)))
            `when`(localDataSource.clientById(TEST_ID)).thenReturn(Either.Left(DatabaseError))

            repository.clientById(TEST_ID)

            verify(remoteDataSource).clientById(eq(TEST_ID))

            assert(repository.clientById(TEST_ID).isLeft)
        }
    }

    private fun generateMockApi(): ClientApi = mock(ClientApi::class.java)

    private fun generateMockDao(): ClientDao = mock(ClientDao::class.java)


    companion object {
        private const val TEST_CODE = 401
        private const val TEST_ID = "4555f7b2"
        private const val TEST_MESSAGE = "testMessage"
    }
}
