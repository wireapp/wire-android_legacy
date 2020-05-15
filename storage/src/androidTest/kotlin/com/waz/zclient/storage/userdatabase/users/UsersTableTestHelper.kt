package com.waz.zclient.storage.userdatabase.users

import android.content.ContentValues
import com.waz.zclient.storage.DbSQLiteOpenHelper


class UsersTableTestHelper private constructor() {

    companion object {
        private const val USERS_TABLE_NAME = "Users"
        private const val USERS_ID_COL = "_id"
        private const val USERS_TEAM_ID_COL = "teamId"
        private const val USERS_NAME_COL = "name"
        private const val USERS_EMAIL_COL = "email"
        private const val USERS_PHONE_COL = "phone"
        private const val USERS_TRACKING_ID_COL = "tracking_id"
        private const val USERS_PICTURE_COL = "picture"
        private const val USERS_ACCENT_ID_COL = "accent"
        private const val USERS_SKEY_COL = "skey"
        private const val USERS_CONNECTION_COL = "connection"
        private const val USERS_CONNECTION_TIMESTAMP_COL = "conn_timestamp"
        private const val USERS_CONNECTION_MESSAGE_COL = "conn_msg"
        private const val USERS_CONVERSATION_COL = "conversation"
        private const val USERS_RELATION_COL = "relation"
        private const val USERS_TIMESTAMP_COL = "timestamp"
        private const val USERS_VERIFIED_COL = "verified"
        private const val USERS_DELETED_COL = "deleted"
        private const val USERS_AVAILABILITY_COL = "availability"
        private const val USERS_HANDLE_COL = "handle"
        private const val USERS_PROVIDER_ID_COL = "provider_id"
        private const val USERS_INTEGRATION_ID_COL = "integration_id"
        private const val USERS_EXPIRES_AT_COL = "expires_at"
        private const val USERS_MANAGED_BY_COL = "managed_by"
        private const val USERS_SELF_PERMISSIONS_COL = "self_permissions"
        private const val USERS_COPY_PERMISSIONS_COL = "copy_permissions"
        private const val USERS_CREATED_BY_COL = "created_by"

        fun insertUser(
            id: String,
            teamId: String?,
            name: String?,
            email: String?,
            phone: String?,
            trackingId: String?,
            picture: String?,
            accentId: Int?,
            sKey: String?,
            connection: String?,
            connectionTimestamp: Int?,
            connectionMessage: String?,
            conversation: String?,
            relation: String?,
            timestamp: Int?,
            verified: String?,
            deleted: Boolean?,
            availability: Int?,
            handle: String?,
            providerId: String?,
            integrationId: String?,
            expiresAt: Int?,
            managedBy: String?,
            selfPermission: Int?,
            copyPermission: Int?,
            createdBy: String?,
            openHelper: DbSQLiteOpenHelper
        ) {
            val contentValues = ContentValues().also {
                it.put(USERS_ID_COL, id)
                it.put(USERS_TEAM_ID_COL, teamId)
                it.put(USERS_NAME_COL, name)
                it.put(USERS_EMAIL_COL, email)
                it.put(USERS_PHONE_COL, phone)
                it.put(USERS_TRACKING_ID_COL, trackingId)
                it.put(USERS_PICTURE_COL, picture)
                it.put(USERS_ACCENT_ID_COL, accentId)
                it.put(USERS_SKEY_COL, sKey)
                it.put(USERS_CONNECTION_COL, connection)
                it.put(USERS_CONNECTION_TIMESTAMP_COL, connectionTimestamp)
                it.put(USERS_CONNECTION_MESSAGE_COL, connectionMessage)
                it.put(USERS_CONVERSATION_COL, conversation)
                it.put(USERS_RELATION_COL, relation)
                it.put(USERS_TIMESTAMP_COL, timestamp)
                it.put(USERS_VERIFIED_COL, verified)
                it.put(USERS_DELETED_COL, deleted)
                it.put(USERS_AVAILABILITY_COL, availability)
                it.put(USERS_HANDLE_COL, handle)
                it.put(USERS_PROVIDER_ID_COL, providerId)
                it.put(USERS_INTEGRATION_ID_COL, integrationId)
                it.put(USERS_EXPIRES_AT_COL, expiresAt)
                it.put(USERS_MANAGED_BY_COL, managedBy)
                it.put(USERS_SELF_PERMISSIONS_COL, selfPermission)
                it.put(USERS_COPY_PERMISSIONS_COL, copyPermission)
                it.put(USERS_CREATED_BY_COL, createdBy)
            }

            openHelper.insertWithOnConflict(
                tableName = USERS_TABLE_NAME,
                contentValues = contentValues
            )
        }
    }
}
