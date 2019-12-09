package com.waz.zclient.user.data


import com.waz.zclient.user.data.mapper.toUser
import com.waz.zclient.user.data.source.UsersDataSource
import com.waz.zclient.user.data.source.local.UsersLocalDataSource
import com.waz.zclient.user.data.source.remote.UsersRemoteDataSource
import com.waz.zclient.user.domain.model.User
import io.reactivex.Completable
import io.reactivex.Single

class UsersRepository constructor(private val remoteDataSource: UsersRemoteDataSource = UsersRemoteDataSource(),
                                  private val localDataSource: UsersLocalDataSource = UsersLocalDataSource()) : UsersDataSource {

    override fun profile(): Single<User> = localDataSource.profile().onErrorResumeNext(remoteDataSource.profile().doOnSuccess { localDataSource.add(it) }).map { it.toUser() }
    override fun changeHandle(value: String): Completable = remoteDataSource.changeHandle(value).doOnComplete { localDataSource.changeHandle(value) }
    override fun changeEmail(value: String): Completable = remoteDataSource.changeEmail(value).doOnComplete { localDataSource.changeEmail(value) }
    override fun changePhone(value: String): Completable = remoteDataSource.changePhone(value).doOnComplete { localDataSource.changePhone(value) }

}
