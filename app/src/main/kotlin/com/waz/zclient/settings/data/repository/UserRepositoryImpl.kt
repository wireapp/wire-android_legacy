package com.waz.zclient.settings.data.repository

import com.waz.zclient.settings.data.model.UserEntity
import com.waz.zclient.settings.data.source.remote.UserRemoteDataSource
import com.waz.zclient.settings.data.source.remote.UserRemoteDataSourceImpl
import io.reactivex.Single

class UserRepositoryImpl : UserRepository {

    private val remoteDataSource : UserRemoteDataSource = UserRemoteDataSourceImpl()

    override fun getUserProfile(): Single<UserEntity> = remoteDataSource.getUserProfile()
}
