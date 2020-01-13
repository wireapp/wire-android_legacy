package com.waz.zclient.devices.data.source.remote.model

import com.google.gson.annotations.SerializedName
import com.waz.zclient.core.extension.empty

data class ClientApi(
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
    val _class: String,

    @SerializedName("model")
    val model: String,

    @SerializedName("location")
    val location: ClientLocationApi
) {
    companion object {
        val EMPTY = ClientApi(
            String.empty(), String.empty(), String.empty(), String.empty(), String.empty(), String.empty(),
            String.empty(), ClientLocationApi.EMPTY
        )
    }
}

data class ClientLocationApi(
    @SerializedName("lon")
    val long: Double,

    @SerializedName("lat")
    val lat: Double
) {
    companion object {
        val EMPTY = ClientLocationApi(Double.NaN, Double.NaN)
    }
}
