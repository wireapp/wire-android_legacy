package com.waz.zclient.shared.user

import kotlinx.coroutines.flow.Flow

interface UsersRepository {
    suspend fun profileDetails(): Flow<User>
}
