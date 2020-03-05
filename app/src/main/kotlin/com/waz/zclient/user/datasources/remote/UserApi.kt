package com.waz.zclient.user.datasources.remote

import com.google.gson.annotations.SerializedName

data class UserApi(
    @SerializedName("id")
    var id: String? = null,

    @SerializedName("name")
    var name: String? = null,

    @SerializedName("handle")
    var handle: String? = null,

    @SerializedName("email")
    var email: String? = null,

    @SerializedName("phone")
    var phone: String? = null,

    @SerializedName("picture")
    var pictures: List<String>? = null,

    @SerializedName("accent_id")
    var accentId: Int? = null,

    @SerializedName("deleted")
    var deleted: Int? = null,

    @SerializedName("managed_by")
    var managedBy: String? = null
)
