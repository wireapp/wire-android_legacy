package com.waz.zclient.core.backend.datasources.local

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.storage.pref.backend.BackendPreferences

object InvalidBackendConfig : FeatureFailure()

class BackendLocalDataSource(
    private val backendPreferences: BackendPreferences,
    private val customBackendConfig: CustomBackendPreferences
) {
    fun getCustomBackendConfig(): Either<Failure, CustomBackendPreferences> =
        if (customBackendConfig.isValid()) {
            Either.Right(customBackendConfig)
        } else Either.Left(InvalidBackendConfig)

    fun updateCustomBackendConfig(configUrl: String, backendPreferences: CustomBackendPreferences) {
        with(this.backendPreferences) {
            customConfigUrl = configUrl
            environment = backendPreferences.title
            baseUrl = backendPreferences.prefEndpoints.backendUrl
            blacklistUrl = backendPreferences.prefEndpoints.blacklistUrl
            accountsUrl = backendPreferences.prefEndpoints.accountsUrl
            teamsUrl = backendPreferences.prefEndpoints.teamsUrl
            websiteUrl = backendPreferences.prefEndpoints.websiteUrl
        }
    }
}
