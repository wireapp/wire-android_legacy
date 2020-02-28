package com.waz.zclient.user.data.source.local

import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.functional.Either
import com.waz.zclient.core.network.requestDatabase
import com.waz.zclient.storage.db.users.model.UserEntity
import com.waz.zclient.storage.db.users.service.UserDao
import com.waz.zclient.storage.pref.GlobalPreferences
import kotlinx.coroutines.flow.Flow

class UsersLocalDataSource constructor(
    private val userService: UserDao,
    globalPreferences: GlobalPreferences
) {

    private val userId = globalPreferences.activeUserId

    fun profileDetails(): Flow<UserEntity> = userService.byId(userId)

    suspend fun insertUser(user: UserEntity) = requestDatabase { userService.insert(user) }

    suspend fun changeName(value: String) = requestDatabase { userService.updateName(userId, value) }

    suspend fun changeHandle(value: String) = requestDatabase { userService.updateHandle(userId, value) }

    suspend fun changeEmail(value: String) = requestDatabase { userService.updateEmail(userId, value) }

    suspend fun changePhone(value: String) = requestDatabase { userService.updatePhone(userId, value) }

    suspend fun deletePhone() = requestDatabase { userService.updatePhone(userId, String.empty()) }

    fun currentUserId() = Either.Right(userId)
}
