package com.waz.zclient.framework.livedata

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

fun <T> LiveData<T>.observeOnce(onChangeHandler: (T) -> Unit) {
    val observer = OneTimeObserver(handler = onChangeHandler)
    observe(observer, observer)
}

/**
 * A function that suspends the current coroutine until the LiveData's value changes. Then it
 * resumes the coroutine with the new value.
 *
 * @throws TimeoutException if the value of LiveData is not updated within [timeout] seconds.
 */
suspend fun <T> LiveData<T>.awaitValue(timeout: Long = 2L): T = suspendCoroutine { cont ->
    val latch = CountDownLatch(1)

    val observer = OneTimeObserver<T> {
        latch.countDown()
        cont.resume(it)
    }
    this.observe(observer, observer)

    if (!latch.await(timeout, TimeUnit.SECONDS)) {
        cont.resumeWithException(
            TimeoutException("Didn't receive LiveData value after $timeout seconds")
        )
    }
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
