package com.waz.zclient.core.backend.mapper

import com.waz.zclient.core.backend.datasources.local.CustomBackendPrefEndpoints
import com.waz.zclient.core.backend.datasources.local.CustomBackendPrefResponse
import com.waz.zclient.core.backend.datasources.remote.CustomBackendResponse
import com.waz.zclient.core.backend.datasources.remote.CustomBackendResponseEndpoints
import com.waz.zclient.core.backend.usecase.CustomBackend
import com.waz.zclient.core.backend.usecase.CustomBackendEndpoint

class BackendMapper {

    fun toCustomBackend(backendResponse: CustomBackendResponse) = CustomBackend(
        title = backendResponse.title,
        endpoints = toCustomBackendEndpoint(backendResponse.endpoints)
    )

    private fun toCustomBackendEndpoint(endpointResponse: CustomBackendResponseEndpoints) =
        CustomBackendEndpoint(
            backendUrl = endpointResponse.backendUrl,
            backendWsUrl = endpointResponse.backendWsUrl,
            blacklistUrl = endpointResponse.blacklistUrl,
            teamsUrl = endpointResponse.teamsUrl,
            accountsUrl = endpointResponse.accountsUrl,
            websiteUrl = endpointResponse.websiteUrl
        )

    fun toCustomBackend(backendResponse: CustomBackendPrefResponse) = CustomBackend(
        title = backendResponse.title,
        endpoints = toCustomBackendEndpoint(backendResponse.prefEndpoints)
    )

    private fun toCustomBackendEndpoint(endpointResponse: CustomBackendPrefEndpoints) =
        CustomBackendEndpoint(
            backendUrl = endpointResponse.backendUrl,
            blacklistUrl = endpointResponse.blacklistUrl,
            teamsUrl = endpointResponse.teamsUrl,
            accountsUrl = endpointResponse.accountsUrl,
            websiteUrl = endpointResponse.websiteUrl
        )

    fun toCustomPrefBackend(customBackend: CustomBackend) = CustomBackendPrefResponse(
        title = customBackend.title,
        prefEndpoints = toCustomPrefEndpoints(customBackend.endpoints)
    )

    private fun toCustomPrefEndpoints(endpoints: CustomBackendEndpoint) = CustomBackendPrefEndpoints(
        backendUrl = endpoints.backendUrl,
        blacklistUrl = endpoints.blacklistUrl,
        teamsUrl = endpoints.teamsUrl,
        accountsUrl = endpoints.accountsUrl,
        websiteUrl = endpoints.websiteUrl
    )
}
