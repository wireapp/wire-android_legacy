package com.waz.zclient.core.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

abstract class UseCase<out Type, in Params> where Type : Any {

    abstract suspend fun run(params: Params): Either<Failure, Type>

    //All the references to this will be replaced by runUseCase
    open operator fun invoke(
        scope: CoroutineScope,
        params: Params,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        onResult: (Either<Failure, Type>) -> Unit = {}
    ) {
        val backgroundJob = scope.async(dispatcher) { run(params) }
        scope.launch { onResult(backgroundJob.await()) }
    }
}

fun <T, P> CoroutineScope.runUseCase(
    useCase: UseCase<T, P>,
    params: P, dispatcher:
    CoroutineDispatcher = Dispatchers.IO,
    onResult: ((Either<Failure, T>) -> Unit) = {}) where T: Any {

    val backgroundJob = async(dispatcher) { useCase.run(params) }
    launch { onResult(backgroundJob.await()) }
}
