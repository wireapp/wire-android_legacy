package com.waz.client.util

import org.mockito.Mockito

fun <T> anyParam(): T {
    Mockito.any<T>()
    return uninitialized()
}

private fun <T> uninitialized(): T = null as T
