package com.waz.zclient.user.data.source.local

import com.waz.zclient.ContextProvider
import com.waz.zclient.ZApplication
import com.waz.zclient.roomdb.UserDatabase
import com.waz.zclient.roomdb.dao.UserDao
import com.waz.zclient.roomdb.model.UserEntity
import io.reactivex.Completable
import io.reactivex.Single

class UserLocalDataSourceImpl : UserLocalDataSource {

    //TODO Temporary userId : should be fetched from GlobalShared Prefs
    private val userId = "aa4e0112-bc8c-493e-8677-9fde2edf3567"
    private val userDatabase: UserDatabase = UserDatabase.getInstance(ContextProvider.getApplicationContext(),userId)
    private val userDao: UserDao = userDatabase.userDao()


    override fun addUser(user: UserEntity): Completable = userDao.insert(user)
    override fun profile(): Single<UserEntity> = userDao.userById(userId)
    override fun name(name: String): Completable = userDao.updateName(userId,name)
    override fun handle(handle: String): Completable = userDao.updateHandle(userId,handle)
    override fun email(email: String): Completable = userDao.updateEmail(userId,email)
    override fun phone(phone: String): Completable = userDao.updatePhone(userId,phone)

}
