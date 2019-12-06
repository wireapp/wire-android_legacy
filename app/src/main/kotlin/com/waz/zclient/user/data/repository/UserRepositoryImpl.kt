package com.waz.zclient.user.data.repository


import com.waz.zclient.user.data.mapper.toUser
import com.waz.zclient.user.data.source.local.UserLocalDataSource
import com.waz.zclient.user.data.source.local.UserLocalDataSourceImpl
import com.waz.zclient.user.data.source.remote.UserRemoteDataSource
import com.waz.zclient.user.data.source.remote.UserRemoteDataSourceImpl
import com.waz.zclient.user.domain.model.User
import io.reactivex.Completable
import io.reactivex.Single

class UserRepositoryImpl : UserRepository {

    private val remoteDataSource: UserRemoteDataSource = UserRemoteDataSourceImpl()
    private val localDataSource: UserLocalDataSource = UserLocalDataSourceImpl()

    override fun profile(): Single<User> = localDataSource.profile().map{ it.toUser() }//.onErrorResumeNext(remoteDataSource.profile().doOnSuccess { localDataSource.addUser(it) }).map{ it.toUser() }
    override fun name(name: String): Completable = remoteDataSource.name(name).doOnComplete { localDataSource.name(name) }
    override fun handle(handle: String): Completable = remoteDataSource.handle(handle).doOnComplete { localDataSource.handle(handle) }
    override fun email(email: String): Completable = remoteDataSource.email(email).doOnComplete { localDataSource.email(email) }
    override fun phone(phone: String): Completable = remoteDataSource.phone(phone).doOnComplete { localDataSource.phone(phone) }

}
