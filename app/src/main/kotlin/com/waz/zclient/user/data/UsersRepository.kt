package com.waz.zclient.user.data


import com.waz.zclient.user.data.mapper.toUser
import com.waz.zclient.user.data.source.UsersDataSource
import com.waz.zclient.user.data.source.local.UsersLocalDataSource
import com.waz.zclient.user.data.source.remote.UsersRemoteDataSource
import com.waz.zclient.user.domain.model.User
import io.reactivex.Completable
import io.reactivex.Single

class UsersRepository constructor(private val usersRemoteDataSource: UsersRemoteDataSource = UsersRemoteDataSource(),
                                  private val usersLocalDataSource: UsersLocalDataSource = UsersLocalDataSource()) : UsersDataSource {

    override fun profile(): Single<User> = usersLocalDataSource.profile().onErrorResumeNext(usersRemoteDataSource.profile().doOnSuccess { usersLocalDataSource.add(it) }).map { it.toUser() }
    override fun changeHandle(value: String): Completable = usersRemoteDataSource.changeHandle(value).doOnComplete { usersLocalDataSource.changeHandle(value) }
    override fun changeEmail(value: String): Completable = usersRemoteDataSource.changeEmail(value).doOnComplete { usersLocalDataSource.changeEmail(value) }
    override fun changePhone(value: String): Completable = usersRemoteDataSource.changePhone(value).doOnComplete { usersLocalDataSource.changePhone(value) }

}
