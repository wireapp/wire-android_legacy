package com.waz.zclient.core.backend.datasources.remote

import com.google.gson.annotations.SerializedName

data class CustomBackendResponse(
    @SerializedName("title")
    val title: String,

    @SerializedName("endpoints")
    val endpoints: CustomBackendResponseEndpoints
)

data class CustomBackendResponseEndpoints(
    @SerializedName("backendURL")
    val backendUrl: String,

    @SerializedName("backendWSURL")
    val backendWsUrl: String,

    @SerializedName("blackListURL")
    val blacklistUrl: String,

    @SerializedName("teamsURL")
    val teamsUrl: String,

    @SerializedName("accountsURL")
    val accountsUrl: String,

    @SerializedName("websiteURL")
    val websiteUrl: String
)
