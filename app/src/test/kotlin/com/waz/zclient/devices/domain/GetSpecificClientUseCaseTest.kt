package com.waz.zclient.devices.domain

import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.devices.data.ClientsDataSource
import com.waz.zclient.devices.domain.model.Client
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class GetSpecificClientUseCaseTest {

    private lateinit var getSpecificClientUseCase: GetSpecificClientUseCase

    @Mock
    private lateinit var repository: ClientsDataSource

    @Mock
    private lateinit var client: Client

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        getSpecificClientUseCase = GetSpecificClientUseCase(repository)
    }

    @Test
    fun `given client response is successful, then repository returns client`() {
        runBlocking {

            Mockito.`when`(repository.clientById(TEST_ID)).thenReturn(Either.Right(client))

            val params = Params(TEST_ID)
            getSpecificClientUseCase.run(params)

            Mockito.verify(repository).clientById(TEST_ID)

        }
    }

    @Test(expected = CancellationException::class)
    fun `given client response is an error, then repository throws an exception`() {
        runBlocking {
            Mockito.`when`(repository.clientById(TEST_ID)).thenReturn(Either.Left(Failure(TEST_EXCEPTION_MESSAGE)))

            val params = Params(TEST_ID)
            getSpecificClientUseCase.run(params)

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
