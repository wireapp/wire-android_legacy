package com.waz.zclient.core.network.backend

import com.waz.zclient.core.backend.BackendRepository
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class CustomBackendInterceptor(private val backendRepository: BackendRepository) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response =
        backendRepository.configuredUrl()?.let {
            chain.proceed(updateRequestUrl(chain.request(), it))
        } ?: chain.proceed(chain.request())

    private fun updateRequestUrl(request: Request, baseUrl: String): Request {
        val originalUrl = request.url()
        val newUrl = baseUrl + originalUrl.encodedPath()
        return request.newBuilder().url(newUrl).build()
    }
}
