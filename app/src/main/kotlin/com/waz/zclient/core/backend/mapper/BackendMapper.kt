package com.waz.zclient.core.backend.mapper

import com.waz.zclient.core.backend.BackendItem
import com.waz.zclient.core.backend.datasources.local.CustomBackendPrefEndpoints
import com.waz.zclient.core.backend.datasources.local.CustomBackendPreferences
import com.waz.zclient.core.backend.datasources.remote.CustomBackendResponse

class BackendMapper {

    fun toBackendItem(backendPreferences: CustomBackendPreferences): BackendItem =
        with(backendPreferences.prefEndpoints) {
            BackendItem(
                environment = backendPreferences.title,
                baseUrl = backendUrl,
                websocketUrl = websocketUrl,
                blacklistHost = blacklistUrl,
                teamsUrl = teamsUrl,
                accountsUrl = accountsUrl,
                websiteUrl = websiteUrl
            )
        }

    fun toBackendItem(response: CustomBackendResponse): BackendItem = with(response.endpoints) {
        BackendItem(
            environment = response.title,
            baseUrl = backendUrl,
            websocketUrl = backendWsUrl,
            blacklistHost = blacklistUrl,
            teamsUrl = teamsUrl,
            accountsUrl = accountsUrl,
            websiteUrl = websiteUrl
        )
    }

    fun toPreference(backendItem: BackendItem): CustomBackendPreferences = with(backendItem) {
        CustomBackendPreferences(
            title = environment,
            prefEndpoints = CustomBackendPrefEndpoints(
                backendUrl = baseUrl,
                websocketUrl = websocketUrl,
                blacklistUrl = blacklistHost,
                teamsUrl = teamsUrl,
                accountsUrl = accountsUrl,
                websiteUrl = websiteUrl
            )
        )
    }
}
