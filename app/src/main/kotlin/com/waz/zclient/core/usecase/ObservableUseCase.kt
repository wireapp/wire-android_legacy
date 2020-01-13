package com.waz.zclient.core.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.GenericUseCaseError
import com.waz.zclient.core.functional.Either
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

@ExperimentalCoroutinesApi
abstract class ObservableUseCase<out Type, in Params> {

    abstract suspend fun run(params: Params): Flow<Type>

    open operator fun invoke(
        scope: CoroutineScope,
        params: Params,
        onResult: (Either<Failure, Type>) -> Unit = {}) {
        val backgroundJob = scope.async(Dispatchers.IO) { run(params) }
        scope.launch {
            try {
                backgroundJob.await().collect { onResult(Either.Right(it)) }
            } catch (e: Throwable) {
                onResult(Either.Left(GenericUseCaseError(e)))
            }
        }
    }
}

