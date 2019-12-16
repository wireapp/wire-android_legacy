package com.waz.zclient.devices.data.source.local

import com.waz.zclient.framework.mockito.eq
import com.waz.zclient.storage.db.clients.service.ClientDbService
import com.waz.zclient.storage.db.clients.model.ClientEntity
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
    private lateinit var clientEntity: ClientEntity

    @Mock
    private lateinit var clientDao: ClientDbService

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        localDataSource = ClientsLocalDataSource(clientDao)
    }

    @Test
    fun `Given clientById() is called, when dao result is successful, then return the data`() {
        runBlocking {

            Mockito.`when`(clientDao.clientById(TEST_CLIENT_ID)).thenReturn(clientEntity)

            localDataSource.clientById(TEST_CLIENT_ID)

            Mockito.verify(clientDao).clientById(eq(TEST_CLIENT_ID))

            assert(localDataSource.clientById(TEST_CLIENT_ID).isRight)
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given clientById() is called, when dao result is canceled, then return an error`() {
        runBlocking {

            Mockito.`when`(clientDao.clientById(TEST_CLIENT_ID)).thenReturn(clientEntity)

            localDataSource.clientById(TEST_CLIENT_ID)

            Mockito.verify(clientDao).clientById(TEST_CLIENT_ID)

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(200)

            assert(localDataSource.clientById(TEST_CLIENT_ID).isLeft)
        }

    }

    @Test
    fun `Given allClients() is called, when dao result is successful, then return the data`() {
        runBlocking {

            Mockito.`when`(clientDao.allClients()).thenReturn(arrayOf(clientEntity))

            localDataSource.allClients()

            Mockito.verify(clientDao).allClients()

            assert(localDataSource.allClients().isRight)
        }
    }

    @Test(expected = CancellationException::class)
    fun `Given allClients() is called, when dao result is canceled, then return an error`() {
        runBlocking {

            Mockito.`when`(clientDao.allClients()).thenReturn(arrayOf(clientEntity))

            localDataSource.allClients()

            Mockito.verify(clientDao).allClients()

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            delay(200)

            assert(localDataSource.allClients().isLeft)
        }
    }

    @Test
    fun `Given updateClients() is called, then update the dao with result`() {
        runBlocking {

            localDataSource.updateClients(arrayOf(clientEntity))

            Mockito.verify(clientDao).updateClients(eq(arrayOf(clientEntity)))
        }
    }

    @Test
    fun `Given updateClient() is called, then update the dao with result`() {
        runBlocking {

            localDataSource.updateClient(clientEntity)

            Mockito.verify(clientDao).updateClient(eq(clientEntity))
        }
    }

    companion object {
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
        private const val TEST_CLIENT_ID = "clientId"
    }

}
