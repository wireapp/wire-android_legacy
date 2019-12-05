package com.waz.zclient.user.data.source.remote

import android.annotation.SuppressLint
import androidx.annotation.VisibleForTesting
import okhttp3.Interceptor
import okhttp3.Response

@VisibleForTesting
const val AUTH_HEADER_TEXT = "Authorization"
class AuthHeaderInterceptor(private val authRetryDelegate: AuthRetryDelegate) : Interceptor {

    var token: String? = null

    var tokenType: String? = null

    @SuppressLint("CheckResult")
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(getRequest(chain))
        if (!authRetryDelegate.retryRequired(response)) {
            return response
        }
        authRetryDelegate.startRetryProcess()
        authRetryDelegate.blockingWaitForRetryResult()
        return chain.proceed(getRequest(chain))
    }

    private fun getRequest(chain: Interceptor.Chain) = chain.request().newBuilder().apply {
        token?.let {
            addHeader(AUTH_HEADER_TEXT, "$tokenType $it")
        }
    }.build()

}
