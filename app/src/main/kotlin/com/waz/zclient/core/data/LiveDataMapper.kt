@file:Suppress("UNCHECKED_CAST")

package com.waz.zclient.core.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.liveData
import com.waz.zclient.core.data.source.remote.RequestResult
import kotlinx.coroutines.Dispatchers
import kotlin.math.log

fun <T, A> resultLiveData(networkCall: suspend () -> RequestResult<A>): LiveData<RequestResult<T>> =
    liveData(Dispatchers.IO) {
        emit(RequestResult.loading<T>())
        val responseStatus = networkCall.invoke()
        if (responseStatus.status == RequestResult.Status.SUCCESS) {
            emit(RequestResult.success(responseStatus.data!! as T))
        } else if (responseStatus.status == RequestResult.Status.ERROR) {
            emit(RequestResult.error(responseStatus.message!!))
        }
    }.distinctUntilChanged()
