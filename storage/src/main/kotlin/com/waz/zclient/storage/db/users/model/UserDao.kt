package com.waz.zclient.storage.db.users.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class UserDao(

    @ColumnInfo(name = "_id")
    @PrimaryKey
    var id: String,

    @ColumnInfo(name = "teamId")
    var teamId: String?,

    @ColumnInfo(name = "name")
    var name: String,

    @ColumnInfo(name = "email")
    var email: String?,

    @ColumnInfo(name = "phone")
    var phone: String?,

    @ColumnInfo(name = "tracking_id")
    var trackingId: String?,

    @ColumnInfo(name = "picture")
    var picture: String?,

    @ColumnInfo(name = "accent")
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
    var deleted: Int,

    @ColumnInfo(name = "availability")
    var availability: Int?,

    @ColumnInfo(name = "handle")
    var handle: String?,

    @ColumnInfo(name = "provider_id")
    var providerId: String?,

    @ColumnInfo(name = "integration_id")
    var integrationId: String?,

    @ColumnInfo(name = "expires_at")
    var expiresAt: Int?,

    @ColumnInfo(name = "managed_by")
    var managedBy: String?,

    @ColumnInfo(name = "self_permissions")
    var selfPermission: Int?,

    @ColumnInfo(name = "copy_permissions")
    var copyPermission: Int?,

    @ColumnInfo(name = "created_by")
    var createdBy: String?
)
