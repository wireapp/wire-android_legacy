package com.waz.zclient.core.network.accesstoken

import com.google.gson.annotations.SerializedName

class AccessTokenPreference(
    @SerializedName("token")
    val token: String,
    @SerializedName("tokenType")
    val tokenType: String,
    @SerializedName("expiresIn")
    val expiresIn: String
)
