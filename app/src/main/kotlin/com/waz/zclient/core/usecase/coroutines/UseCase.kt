package com.waz.zclient.core.usecase.coroutines

import com.waz.zclient.core.data.source.remote.RequestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

abstract class UseCase<out Type, in Params> where Type : Any {

    abstract suspend fun run(params: Params): RequestResult<Type>

    open operator fun invoke(
        scope: CoroutineScope,
        params: Params,
        onResult: (RequestResult<Type>) -> Unit = {}
    ) {
        val backgroundJob = scope.async { run(params) }
        scope.launch { onResult(backgroundJob.await()) }
    }
}
