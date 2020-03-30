package com.waz.zclient.core.network.backend

import com.waz.zclient.core.backend.BackendRepository
import com.waz.zclient.core.extension.foldSuspendable
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class CustomBackendInterceptor(
    private val backendRepository: BackendRepository,
    private val configUrl: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response =
        if (configUrl.isEmpty()) {
            chain.proceed(chain.request())
        } else {
            val request = buildConfigRequest(chain)
            request?.let {
                chain.proceed(it)
            } ?: chain.proceed(chain.request())
        }

    private fun buildConfigRequest(chain: Interceptor.Chain): Request? = runBlocking {
        getCustomBackendConfig().foldSuspendable(
            { null }
        ) {
            chain.request()
                .newBuilder()
                .url(it.endpoints.backendUrl)
                .build()
        }
    }

    private suspend fun getCustomBackendConfig() =
        backendRepository.getCustomBackendConfig(configUrl)
}
