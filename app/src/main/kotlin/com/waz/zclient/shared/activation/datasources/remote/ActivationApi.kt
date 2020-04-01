package com.waz.zclient.shared.activation.datasources.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ActivationApi {

    @POST("$ACTIVATE$SEND")
    suspend fun sendActivationCode(@Body name: SendActivationCodeRequest): Response<Unit>

    companion object {
        private const val ACTIVATE = "/activate"
        private const val SEND = "/send"
    }
}

data class SendActivationCodeRequest(
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("locale") val locale: String? = null,
    @SerializedName("voice_call") val voiceCall: Boolean? = null
)
