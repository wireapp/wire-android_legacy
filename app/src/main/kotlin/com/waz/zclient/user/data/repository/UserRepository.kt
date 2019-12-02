package com.waz.zclient.user.data.repository

import com.waz.zclient.user.domain.model.User
import io.reactivex.Completable
import io.reactivex.Single

interface UserRepository {

    fun profile(): Single<User>
    fun name(name: String): Completable
    fun handle(handle: String): Completable
    fun email(email: String): Completable
    fun phone(phone: String): Completable
}
