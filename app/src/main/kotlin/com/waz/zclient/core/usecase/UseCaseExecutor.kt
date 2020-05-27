package com.waz.zclient.core.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.GenericUseCaseError
import com.waz.zclient.core.functional.Either
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

interface UseCaseExecutor {
    operator fun <T, P> UseCase<T, P>.invoke(
        scope: CoroutineScope,
        params: P,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        onResult: (Either<Failure, T>) -> Unit = {}
    )

    @ExperimentalCoroutinesApi
    operator fun <T, P> ObservableUseCase<T, P>.invoke(
        scope: CoroutineScope,
        params: P,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        onResult: (Either<Failure, T>) -> Unit = {}
    )
}

class DefaultUseCaseExecutor : UseCaseExecutor {

    override operator fun <T, P> UseCase<T, P>.invoke(
        scope: CoroutineScope,
        params: P,
        dispatcher: CoroutineDispatcher,
        onResult: (Either<Failure, T>) -> Unit
    ) {
        val backgroundJob = scope.async(dispatcher) { run(params) }
        scope.launch {
            onResult(backgroundJob.await())
        }
    }

    @ExperimentalCoroutinesApi
    @Suppress("TooGenericExceptionCaught")
    override operator fun <T, P> ObservableUseCase<T, P>.invoke(
        scope: CoroutineScope,
        params: P,
        dispatcher: CoroutineDispatcher,
        onResult: (Either<Failure, T>) -> Unit
    ) {
        val backgroundJob = scope.async(dispatcher) { run(params) }
        scope.launch {
            try {
                backgroundJob.await().collect { onResult(Either.Right(it)) }
            } catch (e: Throwable) {
                onResult(Either.Left(GenericUseCaseError(e)))
            }
        }
    }
}
