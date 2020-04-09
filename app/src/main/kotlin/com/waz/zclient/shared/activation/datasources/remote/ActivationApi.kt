package com.waz.zclient.shared.activation.datasources.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ActivationApi {

    @POST("$ACTIVATE$SEND")
    suspend fun sendActivationCode(@Body name: SendActivationCodeRequest): Response<Unit>

    @POST("$ACTIVATE")
    suspend fun activate(@Body name: ActivationRequest): Response<Unit>

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

data class ActivationRequest(
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("code") val code: String,
    @SerializedName("dryrun") val dryrun: Boolean? = null,
    @SerializedName("label") val label: String? = null
)
