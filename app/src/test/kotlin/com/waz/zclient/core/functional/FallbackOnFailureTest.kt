package com.waz.zclient.core.functional

import com.waz.zclient.UnitTest
import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.ServerError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

@ExperimentalCoroutinesApi
class FallbackOnFailureTest : UnitTest() {

    private interface SuspendHelper {
        suspend fun primaryAction(): Either<Failure, Unit>

        suspend fun fallbackAction(): Either<Failure, Unit>

        suspend fun fallbackSuccessAction(toSave: Unit)
    }

    @Mock
    private lateinit var suspendHelper: SuspendHelper

    private lateinit var primaryAction: suspend () -> Either<Failure, Unit>

    private lateinit var fallbackAction: suspend () -> Either<Failure, Unit>

    private lateinit var fallbackSuccessAction: suspend (Unit) -> Unit

    @Before
    fun setUp() {
        primaryAction = suspendHelper::primaryAction
        fallbackAction = suspendHelper::fallbackAction
        fallbackSuccessAction = suspendHelper::fallbackSuccessAction
    }

    @Test
    fun `given a suspend function, when fallback method called with a fallbackAction, returns EitherFallbackWrapper`() {
        val eitherFallbackWrapper = primaryAction.fallback(fallbackAction)

        eitherFallbackWrapper shouldEqual FallbackOnFailure(primaryAction, fallbackAction)
    }

    @Test
    fun `given a primaryAction with success, when execute called, does not execute fallbackAction`() {
        runBlockingTest {
            `when`(suspendHelper.primaryAction()).thenReturn(Either.Right(Unit))

            FallbackOnFailure(primaryAction, fallbackAction).execute()

            verify(suspendHelper).primaryAction()
            verifyNoMoreInteractions(suspendHelper)
        }
    }

    @Test
    fun `given a primaryAction with failure, when execute called, executes fallbackAction`() {
        runBlockingTest {
            `when`(suspendHelper.primaryAction()).thenReturn(Either.Left(ServerError))

            val fallbackResponse = mock(Either.Left::class.java) as Either.Left<Failure>
            `when`(suspendHelper.fallbackAction()).thenReturn(fallbackResponse)

            FallbackOnFailure(primaryAction, fallbackAction).execute()

            verify(suspendHelper).primaryAction()
            verify(suspendHelper).fallbackAction()
        }
    }

    @Test
    fun `given a fallbackAction with success and fallbackSuccessAction, when execute called, executes fallbackSuccessAction`() {
        runBlockingTest {
            `when`(suspendHelper.primaryAction()).thenReturn(Either.Left(ServerError))
            `when`(suspendHelper.fallbackAction()).thenReturn(Either.Right(Unit))

            FallbackOnFailure(primaryAction, fallbackAction)
                .finally(fallbackSuccessAction)
                .execute()

            verify(suspendHelper).primaryAction()
            verify(suspendHelper).fallbackAction()
            verify(suspendHelper).fallbackSuccessAction(Unit)
        }
    }

    @Test
    fun `given a fallbackAction with failure and fallbackSuccessAction, when execute called, does not execute fallbackSuccessAction`() {
        runBlockingTest {
            `when`(suspendHelper.primaryAction()).thenReturn(Either.Left(ServerError))
            `when`(suspendHelper.fallbackAction()).thenReturn(Either.Left(ServerError))

            FallbackOnFailure(primaryAction, fallbackAction)
                .finally(fallbackSuccessAction)
                .execute()

            verify(suspendHelper).primaryAction()
            verify(suspendHelper).fallbackAction()
            verifyNoMoreInteractions(suspendHelper)
        }
    }
}
