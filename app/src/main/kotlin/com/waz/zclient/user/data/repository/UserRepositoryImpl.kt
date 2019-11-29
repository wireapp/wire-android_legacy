package com.waz.zclient.user.data.repository


import com.waz.zclient.user.data.mapper.UserEntityMapper
import com.waz.zclient.user.data.source.remote.UserRemoteDataSource
import com.waz.zclient.user.domain.model.User
import io.reactivex.Completable
import io.reactivex.Single

class UserRepositoryImpl constructor(
    private val remoteDataSource: UserRemoteDataSource
) : UserRepository {

    private val userEntityMapper = UserEntityMapper()

    override fun getProfile(): Single<User> = remoteDataSource.getProfile().map { userEntityMapper.mapToDomain(it) }
    override fun updateName(name: String): Completable = remoteDataSource.updateName(name)
    override fun updateHandle(handle: String): Completable = remoteDataSource.updateHandle(handle)
    override fun updateEmail(email: String): Completable = remoteDataSource.updateEmail(email)
    override fun updatePhone(phone: String): Completable = remoteDataSource.updatePhone(phone)
}
