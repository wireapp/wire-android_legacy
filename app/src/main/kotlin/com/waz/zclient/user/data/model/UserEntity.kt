package com.waz.zclient.user.data.model

import com.google.gson.annotations.SerializedName

data class UserEntity(
    @SerializedName("email")
    val email: String,
    @SerializedName("phone")
    val phone: String,
    @SerializedName("handle")
    val handle: String,
    @SerializedName("locale")
    val locale: String,
    @SerializedName("managed_by")
    val managedBy: String,
    @SerializedName("accent_id")
    val accentId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("id")
    val id: String,
    @SerializedName("deleted")
    val deleted: Boolean)
