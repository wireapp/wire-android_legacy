package com.waz.zclient.user.mapper

import com.waz.zclient.storage.db.users.model.UserEntity
import com.waz.zclient.user.datasources.remote.UserResponse
import com.waz.zclient.user.User

class UserMapper {

    fun toUser(userResponse: UserResponse) = with(userResponse) {
        User(
            id = id,
            name = name,
            handle = handle,
            email = email,
            phone = phone,
            pictures = pictures,
            accentId = accentId,
            deleted = deleted,
            managedBy = managedBy
        )
    }

    fun toUser(userEntity: UserEntity) = with(userEntity) {
        User(id = id, teamId = teamId, name = name,
            handle = handle, email = email, phone = phone,
            trackingId = trackingId, picture = picture,
            accentId = accentId, sKey = sKey,
            connection = connection, connectionTimestamp = connectionTimestamp,
            connectionMessage = connectionMessage, conversation = conversation, relation = relation,
            timestamp = timestamp, verified = verified, deleted = deleted,
            availability = availability, providerId = providerId,
            integrationId = integrationId, expiresAt = expiresAt, managedBy = managedBy,
            selfPermission = selfPermission, copyPermission = copyPermission,
            createdBy = createdBy
        )
    }

    fun toUserDao(user: User) = with(user) {
        UserEntity(id = id.toString(), teamId = teamId, name = name.toString(),
            handle = handle, email = email, phone = phone,
            trackingId = trackingId, picture = picture,
            accentId = accentId, sKey = sKey,
            connection = connection, connectionTimestamp = connectionTimestamp,
            connectionMessage = connectionMessage, conversation = conversation, relation = relation,
            timestamp = timestamp, verified = verified, deleted = deleted!!.toInt(),
            availability = availability, providerId = providerId,
            integrationId = integrationId, expiresAt = expiresAt, managedBy = managedBy,
            selfPermission = selfPermission, copyPermission = copyPermission,
            createdBy = createdBy
        )
    }
}
