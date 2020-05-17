package com.waz.zclient.core.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

@ExperimentalCoroutinesApi
abstract class ObservableUseCase<out Type, in Params> {

    abstract suspend fun run(params: Params): Flow<Type>
}
