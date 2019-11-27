package com.waz.zclient.settings.presentation.mapper

import com.waz.zclient.settings.presentation.model.UserItem
import com.waz.zclient.user.domain.model.User

class UserItemMapper {
    fun mapFromDomain(user: User): UserItem = UserItem(user.email, user.phone, user.handle, user.name)
}
