package com.waz.zclient.user.data.repository


import com.waz.zclient.user.data.model.UserEntity
import com.waz.zclient.user.data.source.remote.UserRemoteDataSource
import com.waz.zclient.user.data.source.remote.UserRemoteDataSourceImpl
import io.reactivex.Single

class UserRepositoryImpl : UserRepository {

    private val remoteDataSource : UserRemoteDataSource = UserRemoteDataSourceImpl()

    override fun getUserProfile(): Single<UserEntity> = remoteDataSource.getUserProfile()
}
