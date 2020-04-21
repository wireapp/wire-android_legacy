package com.waz.zclient.feature.auth.registration.register.datasources.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface RegisterApi {
    @POST("$REGISTER")
    suspend fun register(@Body registerRequestBody: RegisterRequestBody): Response<UserResponse>

    companion object {
        private const val REGISTER = "/register"
    }
}

data class RegisterRequestBody(
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("team_code") val teamCode: String? = null,
    @SerializedName("locale") val locale: String? = null,
    @SerializedName("accent_id") val accentId: Int? = null,
    @SerializedName("name") val name: String,
    @SerializedName("password") val password: String? = null,
    @SerializedName("team") val team: BindingTeam? = null,
    @SerializedName("invitation_code") val invitationCode: String? = null,
    @SerializedName("assets") val assets: UserAsset? = null,
    @SerializedName("email_code") val emailCode: String? = null,
    @SerializedName("phone_code") val phoneCode: String? = null,
    @SerializedName("label") val label: String? = null
)

data class BindingTeam(
    @SerializedName("icon") val icon: String,
    @SerializedName("name") val name: String,
    @SerializedName("icon_key") val iconKey: String? = null
)

data class UserAsset(
    @SerializedName("size") val size: String,
    @SerializedName("key") val key: String,
    @SerializedName("type") val type: String
)

data class UserResponse(
    @SerializedName("email") val email: String? = null,
    @SerializedName("handle") val handle: String? = null,
    @SerializedName("service") val service: ServiceRef? = null,
    @SerializedName("accent_id") val accentId: Int? = null,
    @SerializedName("name") val name: String,
    @SerializedName("team") val team: String? = null,
    @SerializedName("id") val id: String,
    @SerializedName("deleted") val deleted: Boolean? = null,
    @SerializedName("assets") val assets: List<UserAsset>
)

data class ServiceRef(
    @SerializedName("id") val id: String,
    @SerializedName("provider") val provider: String
)
