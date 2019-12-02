package com.waz.zclient.utilities.extension

import androidx.lifecycle.MutableLiveData
import com.waz.zclient.core.resources.Resource
import com.waz.zclient.core.resources.ResourceStatus

fun <T> MutableLiveData<Resource<T>>.loading() {
    postValue(Resource(status = ResourceStatus.LOADING, data = null, message = null))
}

fun <T> MutableLiveData<Resource<T>>.success(data: T) {
    postValue(Resource(status = ResourceStatus.SUCCESS, data = data, message = null))
}

fun <T> MutableLiveData<Resource<T>>.error(error: Throwable) {
    postValue(Resource(status = ResourceStatus.ERROR, data = null, message = error.localizedMessage))
}
