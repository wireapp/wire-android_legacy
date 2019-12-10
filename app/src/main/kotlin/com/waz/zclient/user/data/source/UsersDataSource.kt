package com.waz.zclient.user.data.source

import com.waz.zclient.user.domain.model.User
import io.reactivex.Completable
import io.reactivex.Single

interface UsersDataSource {

    fun profile(): Single<User>
    fun changeHandle(value: String): Completable
    fun changeEmail(value: String): Completable
    fun changePhone(value: String): Completable
}
