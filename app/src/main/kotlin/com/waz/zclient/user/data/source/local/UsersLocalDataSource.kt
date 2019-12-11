package com.waz.zclient.user.data.source.local


import com.waz.zclient.ContextProvider
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.dao.UserDao
import com.waz.zclient.storage.db.model.UserEntity
import com.waz.zclient.storage.pref.GlobalPreferences

class UsersLocalDataSource constructor(
    private val globalPreferences: GlobalPreferences = GlobalPreferences(ContextProvider.getApplicationContext()),
    userDatabase: UserDatabase = UserDatabase.getInstance(ContextProvider.getApplicationContext(), globalPreferences.activeUserId)) {

    private val userId = globalPreferences.activeUserId

    private val userDao: UserDao = userDatabase.userDao()

    suspend fun add(user: UserEntity): Any = userDao.insert(user)

    suspend fun profile(): UserEntity = userDao.selectById(userId)

    suspend fun changeName(value: String): Any = userDao.updateName(userId, value)

    suspend fun changeHandle(value: String): Any = userDao.updateHandle(userId, value)

    suspend fun changeEmail(value: String): Any = userDao.updateEmail(userId, value)

    suspend fun changePhone(value: String): Any = userDao.updatePhone(userId, value)

}
