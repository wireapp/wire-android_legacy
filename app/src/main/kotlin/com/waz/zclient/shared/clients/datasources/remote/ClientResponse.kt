package com.waz.zclient.shared.clients.datasources.remote

import com.google.gson.annotations.SerializedName

data class ClientResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("time")
    val time: String,

    @SerializedName("label")
    val label: String,

    @SerializedName("cookie")
    val cookie: String?,

    @SerializedName("type")
    val type: String,

    @SerializedName("class")
    val clazz: String,

    @SerializedName("model")
    val model: String,

    @SerializedName("location")
    val location: ClientLocationResponse
)

data class ClientLocationResponse(
    @SerializedName("lon")
    val long: Double,

    @SerializedName("lat")
    val lat: Double
)
