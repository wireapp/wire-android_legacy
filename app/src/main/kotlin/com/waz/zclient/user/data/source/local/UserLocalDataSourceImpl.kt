package com.waz.zclient.user.data.source.local


import com.waz.zclient.ContextProvider
import com.waz.zclient.storage.db.UserDatabase
import com.waz.zclient.storage.db.dao.UserDao
import com.waz.zclient.storage.db.model.UserEntity
import com.waz.zclient.storage.pref.GlobalPreferences
import io.reactivex.Completable
import io.reactivex.Single

class UserLocalDataSourceImpl : UserLocalDataSource {

    private val globalPreferences = GlobalPreferences(ContextProvider.getApplicationContext())
    private val userId = globalPreferences.activeUserId
    private val userDatabase: UserDatabase = UserDatabase.getInstance(ContextProvider.getApplicationContext(), userId)
    private val userDao: UserDao = userDatabase.userDao()


    override fun addUser(user: UserEntity): Completable = userDao.insert(user)
    override fun profile(): Single<UserEntity> = userDao.userById(userId)
    override fun name(name: String): Completable = userDao.updateName(userId, name)
    override fun handle(handle: String): Completable = userDao.updateHandle(userId, handle)
    override fun email(email: String): Completable = userDao.updateEmail(userId, email)
    override fun phone(phone: String): Completable = userDao.updatePhone(userId, phone)

}
