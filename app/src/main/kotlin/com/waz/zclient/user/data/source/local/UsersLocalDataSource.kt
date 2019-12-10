package com.waz.zclient.user.data.source.local


import android.content.Context
import com.waz.zclient.ContextProvider
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.dao.UserDao
import com.waz.zclient.storage.db.model.UserEntity
import com.waz.zclient.storage.pref.GlobalPreferences
import io.reactivex.Completable
import io.reactivex.Single

class UsersLocalDataSource constructor(private val globalPreferences : GlobalPreferences = GlobalPreferences(ContextProvider.getApplicationContext()),
 userDatabase: UserDatabase = UserDatabase.getInstance(ContextProvider.getApplicationContext(), globalPreferences.activeUserId)) {

    private val userId = globalPreferences.activeUserId
    private val userDao: UserDao = userDatabase.userDao()

    fun add(user: UserEntity): Completable = userDao.insert(user)
    fun profile(): Single<UserEntity> = userDao.selectById(userId)
    fun changeName(value: String): Completable = userDao.updateName(userId, value)
    fun changeHandle(value: String): Completable = userDao.updateHandle(userId, value)
    fun changeEmail(value: String): Completable = userDao.updateEmail(userId, value)
    fun changePhone(value: String): Completable = userDao.updatePhone(userId, value)


}
