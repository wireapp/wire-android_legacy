package com.waz.zclient.core.backend.datasources.local

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.storage.pref.backend.BackendPreferences

object InvalidBackendConfig : FeatureFailure()

class BackendLocalDataSource(private val backendPreferences: BackendPreferences) {

    fun environment() = backendPreferences.environment

    private fun readCustomBackendConfig(): CustomBackendPreferences = with(backendPreferences) {
        CustomBackendPreferences(
            title = environment,
            prefEndpoints = CustomBackendPrefEndpoints(
                backendUrl = baseUrl,
                websocketUrl = websocketUrl,
                blacklistUrl = blacklistUrl,
                teamsUrl = teamsUrl,
                accountsUrl = accountsUrl,
                websiteUrl = websiteUrl
            ))
    }

    fun backendConfig(): Either<Failure, CustomBackendPreferences> =
        readCustomBackendConfig().let {
            if (it.isValid()) Either.Right(it)
            else Either.Left(InvalidBackendConfig)
        }

    fun updateBackendConfig(configUrl: String, backendPreferences: CustomBackendPreferences) {
        with(this.backendPreferences) {
            customConfigUrl = configUrl
            environment = backendPreferences.title
            baseUrl = backendPreferences.prefEndpoints.backendUrl
            websocketUrl = backendPreferences.prefEndpoints.websocketUrl
            blacklistUrl = backendPreferences.prefEndpoints.blacklistUrl
            accountsUrl = backendPreferences.prefEndpoints.accountsUrl
            teamsUrl = backendPreferences.prefEndpoints.teamsUrl
            websiteUrl = backendPreferences.prefEndpoints.websiteUrl
        }
    }
}
