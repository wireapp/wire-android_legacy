package com.waz.zclient.core.data.source.remote

data class RequestResult<out T>(val status: Status, val data: T?, val message: String?) {

    enum class Status {
        SUCCESS,
        ERROR,
        LOADING,
    }

    companion object {
        fun <T> success(data: T?): RequestResult<T> {
            return RequestResult(Status.SUCCESS, data, null)
        }

        fun <T> error(message: String, data: T? = null): RequestResult<T> {
            return RequestResult(Status.ERROR, data, message)
        }

        fun <T> loading(data: T? = null): RequestResult<T> {
            return RequestResult(Status.LOADING, data, null)
        }
    }
}
