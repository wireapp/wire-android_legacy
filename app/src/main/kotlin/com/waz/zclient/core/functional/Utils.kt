package com.waz.zclient.core.functional

object Utils {
    fun <T> returning(obj: T, body: (T) -> Unit): T {
        body(obj)
        return obj
    }
}
