package com.waz.zclient.core.network

import retrofit2.Response

class RawResponseRegistry {

    private val listeners = mutableSetOf<Function1<Response<*>, Unit>>()

    fun addRawResponseAction(action: (Response<*>) -> Unit) {
        listeners.add(action)
    }

    fun notifyRawResponseReceived(response: Response<*>) {
        listeners.forEach { it.invoke(response) }
    }
}
