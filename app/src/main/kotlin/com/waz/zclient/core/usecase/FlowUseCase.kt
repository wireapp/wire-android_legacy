package com.waz.zclient.core.usecase

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

abstract class FlowUseCase<out Type, in Params> where Type : Any {

    abstract suspend fun run(params: Params): Flow<Type>

    @UseExperimental(InternalCoroutinesApi::class)
    open operator fun invoke(
        scope: CoroutineScope,
        params: Params,
        onSuccess: (Type) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val backgroundJob = scope.async(Dispatchers.IO) { run(params) }
        scope.launch {
            try {
                backgroundJob.await().collect { onSuccess(it) }

            } catch (e: Throwable) {
                onError(e)
            }
        }
    }
}

