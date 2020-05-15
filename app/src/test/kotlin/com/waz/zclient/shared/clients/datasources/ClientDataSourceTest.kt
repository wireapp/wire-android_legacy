package com.waz.zclient.shared.clients.datasources

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.DatabaseError
import com.waz.zclient.core.exception.ServerError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.eq
import com.waz.zclient.shared.clients.ClientsRepository
import com.waz.zclient.shared.clients.datasources.local.ClientsLocalDataSource
import com.waz.zclient.shared.clients.datasources.remote.ClientResponse
import com.waz.zclient.shared.clients.datasources.remote.ClientsRemoteDataSource
import com.waz.zclient.shared.clients.mapper.ClientMapper
import com.waz.zclient.storage.db.clients.model.ClientEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class ClientDataSourceTest : UnitTest() {

    private lateinit var repository: ClientsRepository

    @Mock
    private lateinit var remoteDataSource: ClientsRemoteDataSource

    @Mock
    private lateinit var localDataSource: ClientsLocalDataSource

    @Mock
    private lateinit var clientMapper: ClientMapper

    @Before
    fun setup() {
        repository = ClientsDataSource(remoteDataSource, localDataSource, clientMapper)
    }

    @Test
    fun `Given getAllClients() is called, when the local data source succeeded, then map the data response to domain`() {
        runBlockingTest {
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
        runBlockingTest {
            `when`(remoteDataSource.allClients()).thenReturn(Either.Right(listOf(generateMockApi())))
            `when`(localDataSource.allClients()).thenReturn(Either.Left(DatabaseError))

            repository.allClients()

            verify(remoteDataSource).allClients()

            remoteDataSource.allClients().map {
                val listOfClients = verify(clientMapper).toListOfClients(it)
                runBlockingTest { verify(localDataSource).updateClients(clientMapper.toListOfClientDao(listOfClients)) }
            }
        }
    }

    @Test
    fun `Given allClients() is called, when the local data source failed, remote data source is called and failed, then return error`() {
        runBlockingTest {
            `when`(remoteDataSource.allClients()).thenReturn(Either.Left(ServerError))
            `when`(localDataSource.allClients()).thenReturn(Either.Left(DatabaseError))

            repository.allClients()

            verify(remoteDataSource).allClients()

            assert(repository.allClients().isLeft)
        }
    }

    @Test
    fun `Given getClientById() is called, when the local data source succeeded, then map the data response to domain`() {
        runBlockingTest {
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
        runBlockingTest {
            `when`(remoteDataSource.clientById(TEST_ID)).thenReturn(Either.Right(generateMockApi()))
            `when`(localDataSource.clientById(TEST_ID)).thenReturn(Either.Left(DatabaseError))

            repository.clientById(TEST_ID)

            verify(remoteDataSource).clientById(eq(TEST_ID))

            remoteDataSource.clientById(TEST_ID).map { clientApi ->
                val client = verify(clientMapper).toClient(clientApi)
                runBlockingTest { verify(localDataSource).updateClient(clientMapper.toClientDao(client)) }
            }
        }
    }

    @Test
    fun `Given getClientById() is called, when the local data source failed, remote data source is called and failed, then return error`() {
        runBlockingTest {
            `when`(remoteDataSource.clientById(TEST_ID)).thenReturn(Either.Left(ServerError))
            `when`(localDataSource.clientById(TEST_ID)).thenReturn(Either.Left(DatabaseError))

            repository.clientById(TEST_ID)

            verify(remoteDataSource).clientById(eq(TEST_ID))

            assert(repository.clientById(TEST_ID).isLeft)
        }
    }

    private fun generateMockApi(): ClientResponse = mock(ClientResponse::class.java)

    private fun generateMockDao(): ClientEntity = mock(ClientEntity::class.java)


    companion object {
        private const val TEST_ID = "4555f7b2"
    }
}
