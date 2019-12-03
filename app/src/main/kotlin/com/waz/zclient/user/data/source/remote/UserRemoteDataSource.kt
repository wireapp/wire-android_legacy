package com.waz.zclient.user.data.source.remote

import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single

interface UserRemoteDataSource {

    fun profile(): Single<UserEntity>
    fun name(name: String): Completable
    fun handle(handle: String): Completable
    fun email(email: String): Completable
    fun phone(phone: String): Completable
}
