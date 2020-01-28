package com.waz.zclient.storage.db.accountdata

import androidx.room.Dao
import androidx.room.Query

//TODO: Add dao instrumentation tests
@Dao
interface ActiveAccountsDao {

    @Query("SELECT access_token from ActiveAccounts WHERE _id = :userId")
    suspend fun accessToken(userId: String): AccessTokenEntity?

    @Query("UPDATE ActiveAccounts SET access_token = :accessToken WHERE _id = :userId")
    suspend fun updateAccessToken(userId: String, accessToken: AccessTokenEntity)

    @Query("SELECT cookie from ActiveAccounts WHERE _id = :userId")
    suspend fun refreshToken(userId: String): String?

    @Query("UPDATE ActiveAccounts SET cookie = :refreshToken WHERE _id = :userId")
    suspend fun updateRefreshToken(userId: String, refreshToken: String)
}
