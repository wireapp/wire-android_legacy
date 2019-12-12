package com.waz.zclient.user.data.source.local


import com.waz.zclient.ContextProvider
import com.waz.zclient.core.network.requestLocal
import com.waz.zclient.core.requests.Either
import com.waz.zclient.core.requests.Failure
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.dao.UserDao
import com.waz.zclient.storage.db.model.UserEntity
import com.waz.zclient.storage.pref.GlobalPreferences

class UsersLocalDataSource constructor(
    private val globalPreferences: GlobalPreferences = GlobalPreferences(ContextProvider.getApplicationContext()),
    userDatabase: UserDatabase = UserDatabase.getInstance(ContextProvider.getApplicationContext(), globalPreferences.activeUserId)) {

    private val userId = globalPreferences.activeUserId

    private val userDao: UserDao = userDatabase.userDao()

    fun add(user: UserEntity): Any = userDao.insert(user)

    suspend fun profile(): Either<Failure, UserEntity> = requestLocal { userDao.selectById(userId) }

    suspend fun changeName(value: String): Any = requestLocal { userDao.updateName(userId, value) }

    suspend fun changeHandle(value: String): Any = requestLocal { userDao.updateHandle(userId, value) }

    suspend fun changeEmail(value: String): Any = requestLocal { userDao.updateEmail(userId, value) }

    suspend fun changePhone(value: String): Any = requestLocal { userDao.updatePhone(userId, value) }


}
