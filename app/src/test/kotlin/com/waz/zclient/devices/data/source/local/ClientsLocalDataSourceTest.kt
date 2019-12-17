package com.waz.zclient.devices.data.source.local

import com.waz.zclient.eq
import com.waz.zclient.storage.db.clients.model.ClientDao
import com.waz.zclient.storage.db.clients.service.ClientDbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class ClientsLocalDataSourceTest {

    private lateinit var localDataSource: ClientsLocalDataSource

    @Mock
    private lateinit var clientDao: ClientDao

    @Mock
    private lateinit var clientDbService: ClientDbService

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        localDataSource = ClientsLocalDataSource(clientDbService)
    }

    @Test
    fun `Given clientById() is called, when dao result is successful, then return the data`() {
        runBlocking {

            Mockito.`when`(clientDbService.clientById(TEST_CLIENT_ID)).thenReturn(clientDao)

            localDataSource.clientById(TEST_CLIENT_ID)

            Mockito.verify(clientDbService).clientById(eq(TEST_CLIENT_ID))

            assert(localDataSource.clientById(TEST_CLIENT_ID).isRight)
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given clientById() is called, when dao result is canceled, then return an error`() {
        runBlocking {

            Mockito.`when`(clientDbService.clientById(TEST_CLIENT_ID)).thenReturn(clientDao)

            localDataSource.clientById(TEST_CLIENT_ID)

            Mockito.verify(clientDbService).clientById(TEST_CLIENT_ID)

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(200)

            assert(localDataSource.clientById(TEST_CLIENT_ID).isLeft)
        }

    }

    @Test
    fun `Given allClients() is called, when dao result is successful, then return the data`() {
        runBlocking {

            Mockito.`when`(clientDbService.allClients()).thenReturn(listOf(clientDao))

            localDataSource.allClients()

            Mockito.verify(clientDbService).allClients()

            assert(localDataSource.allClients().isRight)
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given allClients() is called, when dao result is canceled, then return an error`() {
        runBlocking {

            Mockito.`when`(clientDbService.allClients()).thenReturn(listOf(clientDao))

            localDataSource.allClients()

            Mockito.verify(clientDbService).allClients()

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(200)

            assert(localDataSource.allClients().isLeft)
        }
    }

    @Test
    fun `Given updateClients() is called, then update the dao with result`() {
        runBlocking {

            localDataSource.updateClients(listOf(clientDao))

            Mockito.verify(clientDbService).updateClients(eq(listOf(clientDao)))
        }
    }

    @Test
    fun `Given updateClient() is called, then update the dao with result`() {
        runBlocking {

            localDataSource.updateClient(clientDao)

            Mockito.verify(clientDbService).updateClient(eq(clientDao))
        }
    }

    companion object {
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
        private const val TEST_CLIENT_ID = "clientId"
    }

}
