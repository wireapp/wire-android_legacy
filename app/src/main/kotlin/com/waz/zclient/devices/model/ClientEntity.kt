package com.waz.zclient.devices.model

import com.google.gson.annotations.SerializedName

data class ClientEntity(@SerializedName("cookie") val cookie: String,
                        @SerializedName("time") val time: String,
                        @SerializedName("label") val label: String,
                        @SerializedName("class") val _class: String,
                        @SerializedName("type") val type: String,
                        @SerializedName("id") val id: String,
                        @SerializedName("model") val model: String,
                        @SerializedName("location") val location: Location) {

    override fun toString(): String {
        return "$type \n ID: $id \n Activated: $time"
    }
}

data class Location(@SerializedName("lon") val long: Double,
                    @SerializedName("lat") val lat: Double)
