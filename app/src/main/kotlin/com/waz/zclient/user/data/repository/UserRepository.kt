package com.waz.zclient.user.data.repository

import com.waz.zclient.user.data.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single

interface UserRepository {

    fun getProfile(): Single<UserEntity>
    fun updateName(name:String): Completable
    fun updateHandle(handle:String): Completable
    fun updateEmail(email:String): Completable
    fun updatePhone(phone:String): Completable
}
