package com.waz.client

import com.waz.zclient.user.data.model.UserEntity
import com.waz.zclient.user.domain.model.User

val userEntity = UserEntity(
    "test@wire.com",
    "+49172555544",
    "test",
    "en-DE",
    "wire",
    "0",
    "Test",
    "aa4e0112-bc8c-493e-8677-9fde2edf3567",
    false)

val user = User(
    "test@wire.com",
    "+49172555544",
    "test",
    "en-DE",
    "wire",
    "0",
    "Test",
    "aa4e0112-bc8c-493e-8677-9fde2edf3567",
    false)
