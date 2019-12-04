package com.waz.zclient.settings.presentation.model

import com.waz.zclient.user.domain.model.User


data class UserItem( val name: String?,val handle: String?, val email: String?, val phone: String?){
    constructor(user: User) : this(name = user.name,handle = user.handle,email = user.email,
    phone= user.phone)
}
