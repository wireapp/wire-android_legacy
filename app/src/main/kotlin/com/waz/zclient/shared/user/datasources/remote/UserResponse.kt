package com.waz.zclient.shared.user.datasources.remote

import com.google.gson.annotations.SerializedName

data class UserResponse(

    @SerializedName("email")
    var email: String?,

    @SerializedName("phone")
    var phone: String?,

    @SerializedName("handle")
    var handle: String?,

    @SerializedName("locale")
    var locale: String,

    @SerializedName("managed_by")
    var managedBy: String?,

    @SerializedName("accent_id")
    var accentId: Int?,

    @SerializedName("name")
    var name: String,

    @SerializedName("id")
    var id: String
)
