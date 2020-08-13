package com.waz.zclient.feature.backup.users

import com.waz.zclient.feature.backup.BackUpDataMapper
import com.waz.zclient.storage.db.users.model.UsersEntity

class UsersBackUpDataMapper : BackUpDataMapper<UsersBackUpModel, UsersEntity> {
    override fun fromEntity(entity: UsersEntity) = UsersBackUpModel(
        id = entity.id,
        teamId = entity.teamId,
        name = entity.name,
        email = entity.email,
        phone = entity.phone,
        trackingId = entity.trackingId,
        picture = entity.picture,
        accentId = entity.accentId,
        sKey = entity.sKey,
        connection = entity.connection,
        connectionTimestamp = entity.connectionTimestamp,
        connectionMessage = entity.connectionMessage,
        conversation = entity.conversation,
        relation = entity.relation,
        timestamp = entity.timestamp,
        verified = entity.verified,
        deleted = entity.deleted,
        availability = entity.availability,
        handle = entity.handle,
        providerId = entity.providerId,
        integrationId = entity.integrationId,
        expiresAt = entity.expiresAt,
        managedBy = entity.managedBy,
        selfPermission = entity.selfPermission,
        copyPermission = entity.copyPermission,
        createdBy = entity.createdBy
    )

    override fun toEntity(model: UsersBackUpModel) = UsersEntity(
        id = model.id,
        teamId = model.teamId,
        name = model.name,
        email = model.email,
        phone = model.phone,
        trackingId = model.trackingId,
        picture = model.picture,
        accentId = model.accentId,
        sKey = model.sKey,
        connection = model.connection,
        connectionTimestamp = model.connectionTimestamp,
        connectionMessage = model.connectionMessage,
        conversation = model.conversation,
        relation = model.relation,
        timestamp = model.timestamp,
        verified = model.verified,
        deleted = model.deleted,
        availability = model.availability,
        handle = model.handle,
        providerId = model.providerId,
        integrationId = model.integrationId,
        expiresAt = model.expiresAt,
        managedBy = model.managedBy,
        selfPermission = model.selfPermission,
        copyPermission = model.copyPermission,
        createdBy = model.createdBy
    )
}
