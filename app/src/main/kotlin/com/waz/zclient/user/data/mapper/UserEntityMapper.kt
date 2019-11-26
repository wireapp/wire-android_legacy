package com.waz.zclient.user.data.mapper

import com.waz.zclient.user.data.model.UserEntity
import com.waz.zclient.user.domain.model.User

class UserEntityMapper {
    fun mapToDomain(userEntity: UserEntity): User =
        User(userEntity.email, userEntity.phone, userEntity.handle, userEntity.locale, userEntity.managedBy,
            userEntity.accentId, userEntity.name, userEntity.id, userEntity.deleted)
}
