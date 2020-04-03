package com.waz.zclient.core.backend.di

import com.waz.zclient.core.backend.BackendItem
import org.koin.core.KoinComponent
import org.koin.core.qualifier.named

class BackendConfigScopeManager : KoinComponent {

    private var scope = getOrCreateScope(DEFAULT_CONFIG_ID)

    /**
     * Returns whether the app is still running on the same network configurations as the ones it was first started.
     */
    fun isDefaultConfig(): Boolean = scope.id == DEFAULT_CONFIG_ID

    /**
     * Deletes the old scope and creates a new one for the new configuration.
     */
    fun onConfigChanged(id: String) {
        scope.close()
        scope = getOrCreateScope(id)
    }

    /**
     * Returns the [BackendItem] instance that lives in current scope, if any.
     */
    fun backendItem(): BackendItem = scope.get()

    private fun getOrCreateScope(id: String) = getKoin().getOrCreateScope(id, named(SCOPE_NAME))

    companion object {
        const val SCOPE_NAME = "backendConfigScope"
        private const val DEFAULT_CONFIG_ID = "defaultBackendConfigId"
    }
}
