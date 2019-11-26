package com.waz.zclient.devices.model

import com.google.gson.annotations.SerializedName

data class DeviceEntity(@SerializedName("cookie") val cookie: String,
                        @SerializedName("time") val time: String,
                        @SerializedName("id") val id: String,
                        @SerializedName("type") val type: String,
                        @SerializedName("class") val clientClass: String,
                        @SerializedName("label") val label: String) {

    override fun toString(): String {
        return "$type \n ID: $id \n Activated: $time"
    }
}
