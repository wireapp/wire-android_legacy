package com.waz.zclient.core.usecase

import com.waz.zclient.UnitTest
import com.waz.zclient.any
import com.waz.zclient.core.exception.GenericUseCaseError
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.functional.onFailure
import com.waz.zclient.core.functional.onSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
class DefaultUseCaseExecutorTest : UnitTest() {

    private lateinit var executor: DefaultUseCaseExecutor

    @Mock
    private lateinit var useCase: UseCase<String, Int>

    @Mock
    private lateinit var observableUseCase: ObservableUseCase<String, Int>

    @Before
    fun setUp() {
        executor = DefaultUseCaseExecutor()
    }

    @Test
    fun `given a scope and a use case, when invoke is called on use case, then applies onResult to returned value`() {
        runBlocking {
            val param = 3
            val result = Either.Right("3")
            `when`(useCase.run(param)).thenReturn(result)

            with(executor) {
                useCase(this@runBlocking, param, Dispatchers.IO) {
                    assertEquals(result, it)
                }
            }
        }
    }

    @Test
    fun `given a scope and an observable use case which returns success, when invoke is called, then sends result to onResult`() {
        runBlocking {
            val param = 3
            `when`(observableUseCase.run(param)).thenReturn(flowOf("Success!"))

            with(executor) {
                observableUseCase(this@runBlocking, param, Dispatchers.IO) {
                    assertEquals(Either.Right("Success!"), it)
                }
            }
        }
    }

    @Test
    fun `given a scope and an observable use case which throws exception, when invoke is called, then sends failure to onResult`() {
        runBlocking {
            val param = 3

            val flow = mock(Flow::class.java) as Flow<String>
            `when`(flow.collect(any())).thenThrow(IllegalStateException())

            `when`(observableUseCase.run(param)).thenReturn(flow)

            with(executor) {
                observableUseCase(this@runBlocking, param, Dispatchers.IO) {
                    it.onFailure {
                        assertTrue(it is GenericUseCaseError)
                    }.onSuccess {
                        fail("Expected a Failure but got a Success")
                    }
                }
            }
        }
    }
}
