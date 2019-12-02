package com.waz.zclient.utilities.extension


import androidx.lifecycle.MutableLiveData
import com.waz.zclient.settings.presentation.model.Resource
import com.waz.zclient.settings.presentation.model.ResourceStatus

fun <T> MutableLiveData<Resource<T>>.setLoading(error: Throwable) {
    postValue(Resource(status = ResourceStatus.LOADING, data = null, message = null))
}


fun <T> MutableLiveData<Resource<T>>.setSuccess(data: T) {
    postValue(Resource(status = ResourceStatus.SUCCESS, data = data, message = null))
}

fun <T> MutableLiveData<Resource<T>>.setError(error: Throwable) {
    postValue(Resource(status = ResourceStatus.ERROR, data = null, message = error.localizedMessage))
}
