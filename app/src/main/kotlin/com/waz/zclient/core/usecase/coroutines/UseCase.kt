package com.waz.zclient.core.usecase.coroutines

import androidx.lifecycle.LiveData
import com.waz.zclient.core.data.source.remote.RequestResult

interface UseCase<in P, T> {

    fun execute(params: P): LiveData<RequestResult<T>>
}
