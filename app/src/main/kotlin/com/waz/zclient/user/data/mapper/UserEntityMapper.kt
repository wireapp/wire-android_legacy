package com.waz.zclient.user.data.mapper

import com.waz.zclient.user.data.model.UserEntity
import com.waz.zclient.user.domain.model.User

class UserEntityMapper {
    fun mapToDomain(userEntity: UserEntity): User =
        User(userEntity.email.toString(), userEntity.phone.toString(), userEntity.handle.toString(), userEntity.locale, userEntity.managedBy.toString(),
            userEntity.accentId.toString(), userEntity.name, userEntity.id, userEntity.deleted)
}
