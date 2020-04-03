package com.waz.zclient.shared.clients.usecases

import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.shared.clients.ClientsRepository
import com.waz.zclient.shared.clients.usecase.GetAllClientsUseCase
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

            `when`(repository.allClients()).thenReturn(Either.Right(listOf()))

            getAllClientsUseCase.run(Unit)

            verify(repository).allClients()

        }
    }

    @Test(expected = CancellationException::class)
    fun `given clients response is an error, then repository throws an exception`() {
        runBlocking {
            `when`(repository.allClients()).thenReturn(Either.Left(NetworkConnection))

            getAllClientsUseCase.run(Unit)

            verify(repository).allClients()

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            assert(repository.allClients().isLeft)
        }
    }

    companion object {
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
    }
}
