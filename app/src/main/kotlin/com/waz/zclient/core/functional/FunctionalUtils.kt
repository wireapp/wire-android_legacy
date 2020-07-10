package com.waz.zclient.core.functional

object FunctionalUtils {
    fun <T> returning(create: () -> T, body: (T) -> Any): T {
        val t = create()
        body(t)
        return t
    }
}