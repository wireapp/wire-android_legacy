package com.waz.zclient.user.data.source.local

import com.waz.zclient.roomdb.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single

interface UserLocalDataSource {

    fun addUser(user: UserEntity): Completable
    fun profile(): Single<UserEntity>
    fun name(name: String): Completable
    fun handle(handle: String): Completable
    fun email(email: String): Completable
    fun phone(phone: String): Completable
}
