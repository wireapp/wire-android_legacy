package com.waz.zclient.shared.clients.usecases

import com.waz.zclient.core.exception.NetworkConnection
import com.waz.zclient.core.functional.Either
import com.waz.zclient.shared.clients.Client
import com.waz.zclient.shared.clients.ClientsRepository
import com.waz.zclient.shared.clients.usecase.GetClientUseCase
import com.waz.zclient.shared.clients.usecase.GetSpecificClientParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class GetClientUseCaseTest {

    private lateinit var getClientUseCase: GetClientUseCase

    @Mock
    private lateinit var repository: ClientsRepository

    @Mock
    private lateinit var client: Client

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        getClientUseCase = GetClientUseCase(repository)
    }

    @Test
    fun `given client response is successful, then repository returns client`() {
        runBlocking {

            Mockito.`when`(repository.clientById(TEST_ID)).thenReturn(Either.Right(client))

            val params = GetSpecificClientParams(TEST_ID)
            getClientUseCase.run(params)

            Mockito.verify(repository).clientById(TEST_ID)

        }
    }

    @Test(expected = CancellationException::class)
    fun `given client response is an error, then repository throws an exception`() {
        runBlocking {
            Mockito.`when`(repository.clientById(TEST_ID)).thenReturn(Either.Left(NetworkConnection))

            val params = GetSpecificClientParams(TEST_ID)
            getClientUseCase.run(params)

            Mockito.verify(repository).clientById(TEST_ID)

            cancel(CancellationException(TEST_EXCEPTION_MESSAGE))

            assert(repository.clientById(TEST_ID).isLeft)
        }
    }

    companion object {
        private const val TEST_ID = "122345667"
        private const val TEST_EXCEPTION_MESSAGE = "Something went wrong, please try again."
    }
}
