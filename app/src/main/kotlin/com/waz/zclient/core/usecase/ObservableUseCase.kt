package com.waz.zclient.core.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

@ExperimentalCoroutinesApi
interface ObservableUseCase<out Type, in Params> {

    suspend fun run(params: Params): Flow<Type>
}
