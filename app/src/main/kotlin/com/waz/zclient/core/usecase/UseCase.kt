package com.waz.zclient.core.usecase

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class UseCase<out Type, in Params> where Type : Any {

    abstract suspend fun run(params: Params): Either<Failure, Type>

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

abstract class UseCaseTemp<out Type, in Params> where Type : Any {
    protected val dispatcher: CoroutineDispatcher = Dispatchers.IO

    abstract suspend fun run(params: Params): Either<Failure, Type>

    suspend operator fun invoke(params: Params) = withContext(dispatcher) {
        run(params)
    }
}
