package com.waz.zclient.user.data.source.remote

import android.annotation.SuppressLint
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean

interface AuthRetryDelegate {

    fun retryRequired(response: Response): Boolean

    fun getRetryObservable(): Observable<Boolean>

    fun startRetryProcess()

    fun onRetryFinished()

    fun blockingWaitForRetryResult()
}

private const val ERROR_CODE_UNAUTHORIZED = 401
class AuthRetryDelegateImpl : AuthRetryDelegate {

    private val waitForRetry = PublishSubject.create<Boolean>()

    private var retryInProgress = AtomicBoolean(false)

    override fun retryRequired(response: Response) = response.code() == ERROR_CODE_UNAUTHORIZED

    override fun getRetryObservable(): Observable<Boolean> = waitForRetry

    override fun startRetryProcess() {
        if (retryInProgress.compareAndSet(false, true)) {
            waitForRetry.onNext(true)
        }
    }

    override fun onRetryFinished() {
        retryInProgress.set(false)
        waitForRetry.onNext(false)
    }

    @SuppressLint("CheckResult")
    override fun blockingWaitForRetryResult() {
        waitForRetry.blockingFirst()
    }
}
