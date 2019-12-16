package com.waz.zclient.storage.db.users.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName


@Entity(tableName = "user")
data class UserEntity(

    @SerializedName("id")
    @ColumnInfo(name = "_id")
    @PrimaryKey
    var id: String,

    @ColumnInfo(name = "teamId")
    var teamId: String?,

    @ColumnInfo(name = "name")
    @SerializedName("name")
    var name: String?,

    @ColumnInfo(name = "handle")
    @SerializedName("handle")
    var handle: String?,

    @ColumnInfo(name = "email")
    @SerializedName("email")
    var email: String?,

    @ColumnInfo(name = "phone")
    @SerializedName("phone")
    var phone: String?,

    @ColumnInfo(name = "tracking_id")
    var trackingId: String?,

    @ColumnInfo(name = "picture")
    var pictures: String?,

    @Ignore
    @SerializedName("picture")
    var picture: List<String>?,

    @ColumnInfo(name = "accent")
    @SerializedName("accent_id")
    var accentId: Int?,

    @ColumnInfo(name = "skey")
    var sKey: String?,

    @ColumnInfo(name = "connection")
    var connection: String?,

    @ColumnInfo(name = "conn_timestamp")
    var connectionTimestamp: Long?,

    @ColumnInfo(name = "conn_msg")
    var connectionMessage: String?,

    @ColumnInfo(name = "conversation")
    var conversation: String?,

    @ColumnInfo(name = "relation")
    var relation: String?,

    @ColumnInfo(name = "timestamp")
    var timestamp: Long?,

    @ColumnInfo(name = "display_name")
    var displayName: String?,

    @ColumnInfo(name = "verified")
    var verified: String?,

    @ColumnInfo(name = "deleted")
    @SerializedName("deleted")
    var deleted: Int,

    @ColumnInfo(name = "availability")
    var availability: Int?,

    @ColumnInfo(name = "provider_id")
    var providerId: String?,

    @ColumnInfo(name = "integration_id")
    var integrationId: String?,

    @ColumnInfo(name = "expires_at")
    var expiresAt: Int?,

    @ColumnInfo(name = "managed_by")
    @SerializedName("managed_by")
    var managedBy: String?,

    @ColumnInfo(name = "self_permissions")
    var selfPermission: Int?,

    @ColumnInfo(name = "copy_permissions")
    var copyPermission: Int?,

    @ColumnInfo(name = "created_by")
    var createdBy: String?
) {
    constructor() : this("", "", "", "", "", "", "",
        "", listOf(), 0, "", "", 0,
        "", "", "", 0, "", "",
        0, 0, "", "", 0, "", 0,
        0, "")
}
