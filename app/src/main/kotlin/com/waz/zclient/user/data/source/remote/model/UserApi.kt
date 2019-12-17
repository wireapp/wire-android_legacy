package com.waz.zclient.user.data.source.remote.model

import com.google.gson.annotations.SerializedName

data class UserApi(
    @SerializedName("id")
    var id: String,

    @SerializedName("name")
    var name: String,

    @SerializedName("handle")
    var handle: String?,

    @SerializedName("email")
    var email: String?,

    @SerializedName("phone")
    var phone: String?,

    @SerializedName("picture")
    var pictures: List<String>?,

    @SerializedName("accent_id")
    var accentId: Int?,

    @SerializedName("deleted")
    var deleted: Int,

    @SerializedName("managed_by")
    var managedBy: String?
)
