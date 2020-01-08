package com.waz.zclient.core.extension


import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response

fun <T> Response<T>.toFlow(): Flow<T> = flow { body()?.let { emit(it) }}
