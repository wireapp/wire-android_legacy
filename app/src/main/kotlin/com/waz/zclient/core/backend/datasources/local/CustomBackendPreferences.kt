package com.waz.zclient.core.backend.datasources.local

data class CustomBackendPreferences(
    val title: String,
    val prefEndpoints: CustomBackendPrefEndpoints
) {
    fun isValid() =
        title.isNotEmpty() &&
            prefEndpoints.backendUrl.isNotEmpty() &&
            prefEndpoints.blacklistUrl.isNotEmpty() &&
            prefEndpoints.teamsUrl.isNotEmpty() &&
            prefEndpoints.accountsUrl.isNotEmpty() &&
            prefEndpoints.websiteUrl.isNotEmpty()
}

data class CustomBackendPrefEndpoints(
    val backendUrl: String,
    val blacklistUrl: String,
    val teamsUrl: String,
    val accountsUrl: String,
    val websiteUrl: String
)
