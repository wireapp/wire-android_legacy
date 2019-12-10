package com.waz.zclient.features.clients

import com.google.gson.annotations.SerializedName
import com.waz.zclient.core.extension.empty

data class ClientEntity(@SerializedName("cookie") val cookie: String?,
                        @SerializedName("time") val time: String,
                        @SerializedName("label") val label: String,
                        @SerializedName("class") val _class: String,
                        @SerializedName("type") val type: String,
                        @SerializedName("id") val id: String,
                        @SerializedName("model") val model: String,
                        @SerializedName("location") val location: ClientLocationEntity) {

    companion object {
        fun empty() = ClientEntity(String.empty(),
                                   String.empty(),
                                   String.empty(),
                                   String.empty(),
                                   String.empty(),
                                   String.empty(),
                                   String.empty(),
                                   ClientLocationEntity.empty())
    }
}

data class ClientLocationEntity(@SerializedName("lon") val long: Double,
                                @SerializedName("lat") val lat: Double) {

    companion object { fun empty() = ClientLocationEntity(0.0, 0.0) }
}
