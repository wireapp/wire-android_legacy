package com.waz.zclient.devices.data

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.map
import com.waz.zclient.devices.data.source.local.ClientsLocalDataSource
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.framework.mockito.eq
import com.waz.zclient.storage.clients.model.ClientEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class ClientsRepositoryTest {

    private lateinit var repository: ClientsDataSource

    @Mock
    private lateinit var remoteDataSource: ClientsRemoteDataSource

    @Mock
    private lateinit var localDataSource: ClientsLocalDataSource

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        repository = ClientsRepository.getInstance(remoteDataSource, localDataSource)
    }

    @Test
    fun `Given getAllClients() is called, when the local data source succeeded, then map the data response to domain`() {
        runBlocking {
            `when`(localDataSource.allClients()).thenReturn(Either.Right(arrayOf(generateMockEntity())))

            repository.allClients()

            verify(localDataSource).allClients()

            repository.allClients().map {
                val domainClient = it[0]
                assertMappingIsCorrect(domainClient)
            }
        }
    }


    @Test
    fun `Given getAllClients() is called, when the local data source failed, remote data source is called, then map the data response to domain`() {
        runBlocking {
            `when`(remoteDataSource.allClients()).thenReturn(Either.Right(arrayOf(generateMockEntity())))
            `when`(localDataSource.allClients()).thenReturn(Either.Left(Failure.CancellationError))

            repository.allClients()

            verify(remoteDataSource).allClients()

            repository.allClients().map {
                val domainClient = it[0]
                assertMappingIsCorrect(domainClient)
            }
        }
    }

    @Test
    fun `Given allClients() is called, when the local data source failed, remote data source is called and failed, then return error`() {
        runBlocking {
            `when`(remoteDataSource.allClients()).thenReturn(Either.Left(Failure.ServerError(TEST_CODE, TEST_MESSAGE)))
            `when`(localDataSource.allClients()).thenReturn(Either.Left(Failure.CancellationError)
            )
            repository.allClients()

            verify(remoteDataSource).allClients()

            assert(repository.allClients().isLeft)
        }
    }

    @Test
    fun `Given getClientById() is called, when the local data source succeeded, then map the data response to domain`() {
        runBlocking {
            `when`(localDataSource.clientById(TEST_ID)).thenReturn(Either.Right(generateMockEntity()))

            repository.clientById(TEST_ID)

            verify(localDataSource).clientById(eq(TEST_ID))

            repository.clientById(TEST_ID).map {
                assertMappingIsCorrect(it)
            }
        }
    }

    @Test
    fun `Given getClientById() is called, when the local data source failed, remote data source is called, then map the data response to domain`() {
        runBlocking {
            `when`(remoteDataSource.clientById(TEST_ID)).thenReturn(Either.Right(generateMockEntity()))
            `when`(localDataSource.clientById(TEST_ID)).thenReturn(Either.Left(Failure.CancellationError))

            repository.clientById(TEST_ID)

            verify(remoteDataSource).clientById(eq(TEST_ID))

            repository.clientById(TEST_ID).map {
                assertMappingIsCorrect(it)
            }
        }
    }

    @Test
    fun `Given getClientById() is called, when the local data source failed, remote data source is called and failed, then return error`() {
        runBlocking {
            `when`(remoteDataSource.clientById(TEST_ID)).thenReturn(Either.Left(Failure.ServerError(TEST_CODE, TEST_MESSAGE)))
            `when`(localDataSource.clientById(TEST_ID)).thenReturn(Either.Left(Failure.CancellationError))

            repository.clientById(TEST_ID)

            verify(remoteDataSource).clientById(eq(TEST_ID))

            assert(repository.clientById(TEST_ID).isLeft)
        }
    }

    private fun assertMappingIsCorrect(domainClient: Client?) {
        assert(domainClient?.cookie == TEST_COOKIE)
        assert(domainClient?.time == TEST_TIME)
        assert(domainClient?.label == TEST_LABEL)
        assert(domainClient?._class == TEST_CLASS)
        assert(domainClient?.type == TEST_TYPE)
        assert(domainClient?.id == TEST_ID)
        assert(domainClient?.model == TEST_MODEL)
        assert(domainClient?.location?.long == TEST_LONGITUDE)
        assert(domainClient?.location?.lat == TEST_LATITUDE)
    }

    private fun generateMockEntity(): ClientEntity {
        return ClientEntity(TEST_ID, TEST_TIME, TEST_LABEL, TEST_COOKIE, TEST_TYPE, TEST_CLASS,
            TEST_MODEL, TEST_LATITUDE, TEST_LONGITUDE, TEST_ENC_KEY, TEST_MAC_KEY,
            TEST_LOCATION_NAME, TEST_VERIFICATION)
    }

    @After
    fun tearDown() {
        ClientsRepository.destroyInstance()
    }

    companion object {
        private const val TEST_CODE = 401
        private const val TEST_LONGITUDE = 0.00
        private const val TEST_LATITUDE = 0.00
        private const val TEST_COOKIE = "4555f7b2"
        private const val TEST_TIME = "2019-11-14T11:00:42.482Z"
        private const val TEST_LABEL = "Tester's phone"
        private const val TEST_CLASS = "phone"
        private const val TEST_TYPE = "permanant"
        private const val TEST_ID = "4555f7b2"
        private const val TEST_MODEL = "Samsung"
        private const val TEST_ENC_KEY = "encKey"
        private const val TEST_MAC_KEY = "nackKey"
        private const val TEST_LOCATION_NAME = "locationName"
        private const val TEST_VERIFICATION = "verification"
        private const val TEST_MESSAGE = "testMessage"
    }
}
