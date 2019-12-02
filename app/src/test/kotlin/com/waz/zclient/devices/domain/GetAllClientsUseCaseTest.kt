package com.waz.zclient.devices.domain

import com.waz.zclient.core.resources.Resource
import com.waz.zclient.core.resources.ResourceStatus
import com.waz.zclient.devices.data.ClientsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

class GetAllClientsUseCaseTest {

    private lateinit var getAllClientsUseCase: GetAllClientsUseCase

    @Mock
    private lateinit var repository: ClientsRepository

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        getAllClientsUseCase = GetAllClientsUseCase(repository)
    }

    @Test
    fun `given clients response is successful, then repository returns list of a clients`() {
        runBlocking {

            `when`(repository.getAllClients()).thenReturn(Resource.success(listOf()))

            getAllClientsUseCase.run(Unit)

            verify(repository).getAllClients()

        }
    }

    @Test(expected = CancellationException::class)
    fun `given clients response is an error, then repository throws an exception`() {
        runBlocking {
            `when`(repository.getAllClients()).thenReturn(Resource.error(TEST_EXCEPTION_MESSAGE))

            getAllClientsUseCase.run(Unit)

            verify(repository).getAllClients()

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            assert(repository.getAllClients().status == ResourceStatus.ERROR)
        }
    }

    companion object {
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
    }
}
