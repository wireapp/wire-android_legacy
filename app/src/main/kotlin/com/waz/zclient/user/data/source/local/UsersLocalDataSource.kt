package com.waz.zclient.user.data.source.local


import com.waz.zclient.storage.db.users.model.UserDao
import com.waz.zclient.storage.db.users.service.UserDbService
import com.waz.zclient.storage.pref.GlobalPreferences
import kotlinx.coroutines.flow.Flow

class UsersLocalDataSource constructor(private val userService: UserDbService,
                                       private val globalPreferences: GlobalPreferences) {

    private val userId = globalPreferences.activeUserId

    suspend fun add(user: UserDao) = userService.insert(user)

    suspend  fun profile(): Flow<UserDao> = userService.byId(userId)

    suspend fun changeName(value: String) = userService.updateName(userId, value)

}
