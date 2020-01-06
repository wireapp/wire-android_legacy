package com.waz.zclient.user.data.mapper

import com.waz.zclient.storage.db.users.model.UserDao
import com.waz.zclient.user.data.source.remote.model.UserApi
import com.waz.zclient.user.domain.model.User

class UserMapper {

    fun toUser(userApi: UserApi) = with(userApi) {
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

    fun toUser(userDao: UserDao) = with(userDao) {
        User(id = id, teamId = teamId, name = name,
            handle = handle, email = email, phone = phone,
            trackingId = trackingId, picture = picture,
            accentId = accentId, sKey = sKey,
            connection = connection, connectionTimestamp = connectionTimestamp,
            connectionMessage = connectionMessage, conversation = conversation, relation = relation,
            timestamp = timestamp, displayName = displayName, verified = verified, deleted = deleted,
            availability = availability, providerId = providerId,
            integrationId = integrationId, expiresAt = expiresAt, managedBy = managedBy,
            selfPermission = selfPermission, copyPermission = copyPermission,
            createdBy = createdBy
        )
    }

    fun toUserDao(user: User) = with(user) {
        UserDao(id = id, teamId = teamId, name = name,
            handle = handle, email = email, phone = phone,
            trackingId = trackingId, picture = picture,
            accentId = accentId, sKey = sKey,
            connection = connection, connectionTimestamp = connectionTimestamp,
            connectionMessage = connectionMessage, conversation = conversation, relation = relation,
            timestamp = timestamp, displayName = displayName, verified = verified, deleted = deleted,
            availability = availability, providerId = providerId,
            integrationId = integrationId, expiresAt = expiresAt, managedBy = managedBy,
            selfPermission = selfPermission, copyPermission = copyPermission,
            createdBy = createdBy
        )
    }
}

