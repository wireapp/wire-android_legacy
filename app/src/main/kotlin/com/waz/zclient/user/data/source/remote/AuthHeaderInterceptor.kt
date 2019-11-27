package com.waz.zclient.user.data.source.remote

import android.annotation.SuppressLint
import io.reactivex.subjects.PublishSubject
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean

object AuthHeaderInterceptor : Interceptor {

    private const val ERROR_CODE_UNAUTHORIZED = 401

    @JvmStatic
    var token: String? = null

    @JvmStatic
    var tokenType: String? = null

    @JvmField
    val waitForRetry = PublishSubject.create<Boolean>()

    private var retryInProgress = AtomicBoolean(false)

    @SuppressLint("CheckResult")
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(getRequest(chain))
        if (!retryRequired(response)) {
            return response
        }

        if (retryInProgress.compareAndSet(false, true)) {
            waitForRetry.onNext(true)
        }
        waitForRetry.blockingFirst()
        return chain.proceed(getRequest(chain))
    }

    private fun retryRequired(response: Response) = response.code() == ERROR_CODE_UNAUTHORIZED

    private fun getRequest(chain: Interceptor.Chain) = chain.request().newBuilder().apply {
        token?.let {
            addHeader("Authorization", "$tokenType $it")
        }
    }.build()

    @JvmStatic
    fun onRetryFinished() {
        retryInProgress.set(false)
        waitForRetry.onNext(false)
    }
}
