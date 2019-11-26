package com.waz.zclient.user.data.repository


import com.waz.zclient.user.data.model.UserEntity
import com.waz.zclient.user.data.source.remote.UserRemoteDataSource
import com.waz.zclient.user.data.source.remote.UserRemoteDataSourceImpl
import io.reactivex.Completable
import io.reactivex.Single

class UserRepositoryImpl : UserRepository {

    private val remoteDataSource : UserRemoteDataSource = UserRemoteDataSourceImpl()

    override fun getProfile(): Single<UserEntity> = remoteDataSource.getProfile()
    override fun updateName(name: String): Completable = remoteDataSource.updateName(name)
    override fun updateHandle(handle: String): Completable =  remoteDataSource.updateHandle(handle)
    override fun updateEmail(email: String): Completable = remoteDataSource.updateEmail(email)
    override fun updatePhone(phone: String): Completable = remoteDataSource.updatePhone(phone)
}
