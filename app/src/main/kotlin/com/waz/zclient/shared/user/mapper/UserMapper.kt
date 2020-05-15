package com.waz.zclient.shared.user.mapper

import com.waz.zclient.core.extension.empty
import com.waz.zclient.shared.user.User
import com.waz.zclient.shared.user.datasources.remote.UserResponse
import com.waz.zclient.storage.db.users.model.UserEntity

class UserMapper {

    fun toUser(userResponse: UserResponse) = with(userResponse) {
        User(
            email = email,
            phone = phone,
            handle = handle,
            locale = locale,
            managedBy = managedBy,
            accentId = accentId,
            name = name,
            id = id
        )
    }

    fun toUser(userEntity: UserEntity) = with(userEntity) {
        User(
            id = id,
            teamId = teamId,
            name = name,
            email = email,
            phone = phone,
            trackingId = trackingId,
            picture = picture,
            accentId = accentId,
            sKey = sKey,
            connection = connection,
            connectionTimestamp = connectionTimestamp,
            connectionMessage = connectionMessage,
            conversation = conversation,
            relation = relation,
            timestamp = timestamp,
            verified = verified,
            deleted = deleted,
            availability = availability,
            handle = handle,
            providerId = providerId,
            integrationId = integrationId,
            expiresAt = expiresAt,
            managedBy = managedBy,
            selfPermission = selfPermission,
            copyPermission = copyPermission,
            createdBy = createdBy
        )
    }

    fun toUserEntity(user: User) = with(user) {
        UserEntity(
            id = id,
            teamId = teamId,
            name = name,
            email = email,
            phone = phone,
            trackingId = trackingId,
            picture = picture,
            accentId = accentId ?: DEFAULT_ACCENT_ID_VALUE,
            sKey = sKey ?: DEFAULT_SKEY_VALUE,
            connection = connection ?: DEFAULT_CONNECTION_VALUE,
            connectionTimestamp = connectionTimestamp ?: DEFAULT_CONNECTION_TIMESTAMP_VALUE,
            connectionMessage = connectionMessage,
            conversation = conversation,
            relation = relation ?: DEFAULT_RELATION_VALUE,
            timestamp = timestamp,
            verified = verified ?: DEFAULT_VERIFIED_VALUE,
            deleted = deleted ?: DEFAULT_DELETED_VALUE,
            availability = availability ?: DEFAULT_AVAILABILITY_VALUE,
            handle = handle,
            providerId = providerId,
            integrationId = integrationId,
            expiresAt = expiresAt,
            managedBy = managedBy,
            selfPermission = selfPermission ?: DEFAULT_SELF_PERMISSION_VALUE,
            copyPermission = copyPermission ?: DEFAULT_COPY_PERMISSION_VALUE,
            createdBy = createdBy
        )
    }

    companion object {
        private const val DEFAULT_ACCENT_ID_VALUE = 0
        private val DEFAULT_SKEY_VALUE = String.empty()
        private const val DEFAULT_CONNECTION_VALUE = "unconnected"
        private const val DEFAULT_CONNECTION_TIMESTAMP_VALUE: Int = 0
        private const val DEFAULT_RELATION_VALUE = "Other"
        private const val DEFAULT_VERIFIED_VALUE = "UNKNOWN"
        private const val DEFAULT_DELETED_VALUE = false
        private const val DEFAULT_AVAILABILITY_VALUE = 0
        private const val DEFAULT_SELF_PERMISSION_VALUE = 0
        private const val DEFAULT_COPY_PERMISSION_VALUE = 0
    }
}
