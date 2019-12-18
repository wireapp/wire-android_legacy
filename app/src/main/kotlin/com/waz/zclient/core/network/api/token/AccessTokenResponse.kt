package com.waz.zclient.core.network.api.token

import com.google.gson.annotations.SerializedName

data class AccessTokenResponse(
    @SerializedName("token")
    val token : String,
    @SerializedName("type")
    val type: String,
    @SerializedName("expires")
    val expires: String) {

    companion object  {
        val EMPTY = AccessTokenResponse("","","")
    }
}
