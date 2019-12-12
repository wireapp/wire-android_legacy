package com.waz.zclient.user.data.mapper

import com.waz.zclient.storage.db.model.UserEntity

import com.waz.zclient.user.domain.model.User

fun UserEntity.toUser() = User(id = id, teamId = teamId, name = name, handle = handle, email = email, phone = phone,
    trackingId = trackingId, picture = picture, pictures = pictures,
    accentId = accentId, sKey = sKey,
    connection = connection, connectionTimestamp = connectionTimestamp,
    connectionMessage = connectionMessage, conversation = conversation, relation = relation,
    timestamp = timestamp, displayName = displayName, verified = verified, deleted = deleted,
    availability = availability, providerId = providerId,
    integrationId = integrationId, expiresAt = expiresAt, managedBy = managedBy,
    selfPermission = selfPermission, copyPermission = copyPermission,
    createdBy = createdBy
)
