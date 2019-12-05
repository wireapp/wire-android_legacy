package com.waz.zclient.user.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import com.waz.zclient.user.domain.model.User

@Entity(tableName = "Users")
data class UserEntity(

    @PrimaryKey @ColumnInfo(name = "_id")
    @SerializedName("id")
    val id: String,

    @ColumnInfo(name = "teamId")
    val teamId: String?,

    @ColumnInfo(name = "name")
    @SerializedName("name")
    val name: String?,

    @ColumnInfo(name = "handle")
    @SerializedName("handle")
    val handle: String?,

    @ColumnInfo(name = "email")
    @SerializedName("email")
    val email: String?,

    @ColumnInfo(name = "phone")
    @SerializedName("phone")
    val phone: String?,

    @ColumnInfo(name = "tracking_id")
    val trackingId: String?,

    @ColumnInfo(name = "picture")
    val picture: List<String>?,

    @ColumnInfo(name = "accent")
    @SerializedName("accent_id")
    val accentId: Int?,

    @ColumnInfo(name = "skey")
    val sKey: String?,

    @ColumnInfo(name = "connection")
    val connection: String?,

    @ColumnInfo(name = "conn_timestamp")
    val connectionTimestamp: Long?,

    @ColumnInfo(name = "conn_msg")
    val connectionMessage: String?,

    @ColumnInfo(name = "conversation")
    val conversation: String?,

    @ColumnInfo(name = "relation")
    val relation: String?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long?,

    @ColumnInfo(name = "display_name")
    val displayName: String?,

    @ColumnInfo(name = "verified")
    val verified: String?,

    @ColumnInfo(name = "deleted")
    @SerializedName("deleted")
    val deleted: Int,

    @ColumnInfo(name = "availability")
    val availability: Int?,

    @ColumnInfo(name = "provider_id")
    val providerId: String?,

    @ColumnInfo(name = "integration_id")
    val integrationId: String?,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Int?,

    @ColumnInfo(name = "managed_by")
    @SerializedName("managed_by")
    val managedBy: String?,

    @ColumnInfo(name = "self_permission")
    val selfPermission: Int?,

    @ColumnInfo(name = "copy_permission")
    val copyPermission: Int?,

    @ColumnInfo(name = "created_by")
    val createdBy: String?
) {
    fun toUser() = User(id = id, teamId = teamId, name = name, handle = handle, email = email, phone = phone,
        trackingId = trackingId, picture = picture, accentId = accentId, sKey = sKey,
        connection = connection, connectionTimestamp = connectionTimestamp,
        connectionMessage = connectionMessage, conversation = conversation, relation = relation,
        timestamp = timestamp, displayName = displayName, verified = verified, deleted = deleted,
        availability = availability, providerId = providerId,
        integrationId = integrationId, expiresAt = expiresAt, managedBy = managedBy,
        selfPermission = selfPermission, copyPermission = copyPermission,
        createdBy = createdBy
    )
}
