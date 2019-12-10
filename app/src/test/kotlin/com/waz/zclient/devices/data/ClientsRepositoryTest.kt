package com.waz.zclient.devices.data

import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.map
import com.waz.zclient.storage.clients.model.ClientEntity
import com.waz.zclient.storage.clients.model.ClientLocationEntity
import com.waz.zclient.devices.data.source.remote.ClientsRemoteDataSource
import com.waz.zclient.devices.domain.model.Client
import com.waz.zclient.framework.mockito.eq
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

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        repository = ClientsRepository.getInstance(remoteDataSource)
    }

    @Test
    fun `Given getAllClients() is called, when the remote data source is called, then map the data response to domain`() {
        runBlocking {
            `when`(remoteDataSource.allClients()).thenReturn(Either.Right(arrayOf(generateMockEntity())))

            repository.allClients()

            verify(remoteDataSource).allClients()

            repository.allClients().map {
                val domainClient = it?.get(0)
                assertMappingIsCorrect(domainClient)
            }
        }
    }

    @Test
    fun `Given getClientById() is called, then map the data response to domain`() {
        runBlocking {
            `when`(remoteDataSource.clientById(TEST_ID)).thenReturn(Either.Right(generateMockEntity()))

            repository.clientById(TEST_ID)

            verify(remoteDataSource).clientById(eq(TEST_ID))

            repository.clientById(TEST_ID).map {
                assertMappingIsCorrect(it)
            }
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
        val location = ClientLocationEntity(TEST_LONGITUDE, TEST_LATITUDE)
        return ClientEntity(TEST_COOKIE, TEST_TIME, TEST_LABEL, TEST_CLASS, TEST_TYPE, TEST_ID, TEST_MODEL, location)
    }

    @After
    fun tearDown() {
        ClientsRepository.destroyInstance()
    }

    companion object {
        private const val TEST_LONGITUDE = 0.00
        private const val TEST_LATITUDE = 0.00
        private const val TEST_COOKIE = "4555f7b2"
        private const val TEST_TIME = "2019-11-14T11:00:42.482Z"
        private const val TEST_LABEL = "Tester's phone"
        private const val TEST_CLASS = "phone"
        private const val TEST_TYPE = "permanant"
        private const val TEST_ID = "4555f7b2"
        private const val TEST_MODEL = "Samsung"
    }
}
