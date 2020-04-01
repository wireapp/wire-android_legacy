package com.waz.zclient.core.backend

data class CustomBackend(
    val title: String,
    val endpoints: CustomBackendEndpoint
)

data class CustomBackendEndpoint(
    val backendUrl: String,
    val backendWsUrl: String? = null,
    val blacklistUrl: String,
    val teamsUrl: String,
    val accountsUrl: String,
    val websiteUrl: String
)
