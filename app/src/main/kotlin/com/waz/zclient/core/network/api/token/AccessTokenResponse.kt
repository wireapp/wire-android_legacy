package com.waz.zclient.core.network.api.token

import com.google.gson.annotations.SerializedName
import com.waz.zclient.core.extension.empty

data class AccessTokenResponse(
    @SerializedName("access_token")
    val token: String,
    @SerializedName("token_type")
    val type: String,
    @SerializedName("user")
    val userId: String,
    @SerializedName("expires_in")
    val expiresIn: String
) {

    companion object {
        val EMPTY = AccessTokenResponse(String.empty(), String.empty(), String.empty(), String.empty())
    }
}
