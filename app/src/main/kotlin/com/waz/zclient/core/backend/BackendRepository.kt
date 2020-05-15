package com.waz.zclient.core.backend

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.functional.Either

interface BackendRepository {
    /**
     * Loads a new backend config from given url.
     */
    suspend fun loadBackendConfig(url: String): Either<Failure, BackendItem>

    /**
     * Returns the base url that should be used for network requests in the future, overriding Retrofit's base url.
     *
     * Note that, if the app is running on a custom backend which is configured before, this method also returns null,
     * since custom backend is fetched as the default config when app starts.
     *
     * @return the new url if configured during app's lifetime, null o/w
     */
    fun configuredUrl(): String?

    /**
     * Returns the current [BackendItem] encapsulating the endpoints to be used to perform network requests.
     */
    fun backendConfig(): BackendItem

    /**
     * Fetches a new instance of the [BackendItem] from data sources.
     */
    fun fetchBackendConfig(): BackendItem
}
