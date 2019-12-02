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

    override fun profile(): Single<User> = remoteDataSource.profile().map { userEntityMapper.mapToDomain(it) }
    override fun name(name: String): Completable = remoteDataSource.name(name)
    override fun handle(handle: String): Completable = remoteDataSource.handle(handle)
    override fun email(email: String): Completable = remoteDataSource.email(email)
    override fun phone(phone: String): Completable = remoteDataSource.phone(phone)
}
