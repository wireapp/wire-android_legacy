package com.waz.zclient.storage.db.users.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Users",
    indices = [
        Index(name = "Conversation_id", value = ["_id"]),
        Index(name = "UserData_search_key", value = ["skey"])
    ]
)
data class UserEntity(

    @ColumnInfo(name = "_id")
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "teamId")
    val teamId: String?,

    @ColumnInfo(name = "name", defaultValue = "")
    val name: String,

    @ColumnInfo(name = "email")
    val email: String?,

    @ColumnInfo(name = "phone")
    val phone: String?,

    @ColumnInfo(name = "tracking_id")
    val trackingId: String?,

    @ColumnInfo(name = "picture")
    val picture: String?,

    @ColumnInfo(name = "accent", defaultValue = "0")
    val accentId: Int,

    @ColumnInfo(name = "skey", defaultValue = "")
    val sKey: String,

    @ColumnInfo(name = "connection", defaultValue = "")
    val connection: String,

    @ColumnInfo(name = "conn_timestamp", defaultValue = "0")
    val connectionTimestamp: Int,

    @ColumnInfo(name = "conn_msg")
    val connectionMessage: String?,

    @ColumnInfo(name = "conversation")
    val conversation: String?,

    @ColumnInfo(name = "relation", defaultValue = "")
    val relation: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Int?,

    @ColumnInfo(name = "verified")
    val verified: String?,

    @ColumnInfo(name = "deleted", defaultValue = "0")
    val deleted: Boolean,

    @ColumnInfo(name = "availability", defaultValue = "0")
    val availability: Int,

    @ColumnInfo(name = "handle")
    val handle: String?,

    @ColumnInfo(name = "provider_id")
    val providerId: String?,

    @ColumnInfo(name = "integration_id")
    val integrationId: String?,

    @ColumnInfo(name = "expires_at")
    val expiresAt: Int?,

    @ColumnInfo(name = "managed_by")
    val managedBy: String?,

    @ColumnInfo(name = "self_permissions", defaultValue = "0")
    val selfPermission: Int,

    @ColumnInfo(name = "copy_permissions", defaultValue = "0")
    val copyPermission: Int,

    @ColumnInfo(name = "created_by")
    val createdBy: String?
)
