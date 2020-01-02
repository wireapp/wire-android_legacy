package com.waz.zclient.core.network

import retrofit2.Response

class RawResponseRegistry {

    private val listeners = mutableSetOf<RawResponseListener>()

    fun addRawResponseAction(action: (Response<*>) -> Unit) {
        listeners.add(object : RawResponseListener {
            override fun onRawResponseReceived(response: Response<*>) {
                action(response)
            }
        })
    }

    fun notifyRawResponseReceived(response: Response<*>) {
        listeners.forEach { it.onRawResponseReceived(response) }
    }

    private interface RawResponseListener {
        fun onRawResponseReceived(response: Response<*>)
    }
}
