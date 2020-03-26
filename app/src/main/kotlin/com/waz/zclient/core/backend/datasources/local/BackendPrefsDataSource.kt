package com.waz.zclient.core.backend.datasources.local

import com.waz.zclient.core.exception.Failure
import com.waz.zclient.core.exception.FeatureFailure
import com.waz.zclient.core.functional.Either
import com.waz.zclient.storage.pref.backend.BackendPreferences

object InvalidBackendConfig : FeatureFailure()

class BackendPrefsDataSource(private val backendPreferences: BackendPreferences) {

    private val customBackendConfig = CustomBackendPrefResponse(
        backendPreferences.environment,
        CustomBackendPrefEndpoints(
            backendPreferences.baseUrl,
            backendPreferences.blacklistUrl,
            backendPreferences.teamsUrl,
            backendPreferences.accountsUrl,
            backendPreferences.websiteUrl
        )
    )

    fun getCustomBackendConfig(): Either<Failure, CustomBackendPrefResponse> =
        if (customBackendConfig.isValid()) {
            Either.Right(customBackendConfig)
        } else Either.Left(InvalidBackendConfig)

    fun updateCustomBackendConfig(configUrl: String, backendPrefResponse: CustomBackendPrefResponse) {
        backendPreferences.customConfigUrl = configUrl
        backendPreferences.environment = backendPrefResponse.title
        backendPreferences.baseUrl = backendPrefResponse.prefEndpoints.backendUrl
        backendPreferences.blacklistUrl = backendPrefResponse.prefEndpoints.blacklistUrl
        backendPreferences.accountsUrl = backendPrefResponse.prefEndpoints.accountsUrl
        backendPreferences.teamsUrl = backendPrefResponse.prefEndpoints.teamsUrl
    }
}
