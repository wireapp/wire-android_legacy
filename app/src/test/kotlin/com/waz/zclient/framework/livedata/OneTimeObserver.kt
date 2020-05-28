package com.waz.zclient.framework.livedata

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun <T> LiveData<T>.observeOnce(onChangeHandler: (T) -> Unit) {
    val observer = OneTimeObserver(handler = onChangeHandler)
    observe(observer, observer)
}

/**
 * A function that suspends the current coroutine until the LiveData's value changes. Then it
 * resumes the coroutine with the new value.
 */
suspend fun <T> LiveData<T>.awaitValue(): T = suspendCoroutine { cont ->
    val observer  = OneTimeObserver<T> {
        cont.resume(it)
    }
    this.observe(observer, observer)
}


class OneTimeObserver<T>(private val handler: (T) -> Unit) : Observer<T>, LifecycleOwner {
    private val lifecycle = LifecycleRegistry(this)

    init {
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun getLifecycle(): Lifecycle = lifecycle

    override fun onChanged(t: T) {
        handler(t)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
