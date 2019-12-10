package com.waz.zclient.features.clients

import com.google.gson.annotations.SerializedName

data class ClientEntity(@SerializedName("cookie") val cookie: String?,
                        @SerializedName("time") val time: String,
                        @SerializedName("label") val label: String,
                        @SerializedName("class") val _class: String,
                        @SerializedName("type") val type: String,
                        @SerializedName("id") val id: String,
                        @SerializedName("model") val model: String,
                        @SerializedName("location") val location: ClientLocationEntity)

data class ClientLocationEntity(@SerializedName("lon") val long: Double,
                                @SerializedName("lat") val lat: Double)
