package com.waz.zclient.devices.data.source.remote.model

import com.google.gson.annotations.SerializedName

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
    val location: ClientLocationApi)

data class ClientLocationApi(
    @SerializedName("lon")
    val long: Double,

    @SerializedName("lat")
    val lat: Double)
