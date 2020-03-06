package com.waz.zclient

import com.waz.zclient.storage.db.users.model.UserEntity
import com.waz.zclient.user.datasources.remote.UserResponse
import com.waz.zclient.user.User

val userResponse = UserResponse(
    id = "id",
    name = "name",
    handle = "test",
    email = "test@wire.com",
    phone = "",
    pictures = listOf(),
    accentId = 0,
    deleted = 0,
    managedBy = "test")

val userDao = UserEntity(
    id = "id",
    teamId = "teamId",
    name = "name",
    handle = "test",
    email = "test@wire.com",
    phone = "",
    trackingId = "testId",
    picture = "",
    accentId = 0,
    sKey = "test",
    connection = "test",
    connectionTimestamp = 11838383,
    connectionMessage = "test",
    conversation = "test",
    relation = "test",
    timestamp = 11838383,
    verified = "test",
    deleted = 0,
    availability = 1,
    providerId = "test",
    integrationId = "test",
    expiresAt = 999191919,
    managedBy = "test",
    selfPermission = 0,
    copyPermission = 0,
    createdBy = "test")

val user = User(
    id = "id",
    teamId = "teamId",
    name = "name",
    handle = "test",
    email = "test@wire.com",
    phone = "",
    trackingId = "testId",
    pictures = listOf(),
    picture = "",
    accentId = 0,
    sKey = "test",
    connection = "test",
    connectionTimestamp = 11838383,
    connectionMessage = "test",
    conversation = "test",
    relation = "test",
    timestamp = 11838383,
    displayName = "test",
    verified = "test",
    deleted = 0,
    availability = 1,
    providerId = "test",
    integrationId = "test",
    expiresAt = 999191919,
    managedBy = "test",
    selfPermission = 0,
    copyPermission = 0,
    createdBy = "test")
