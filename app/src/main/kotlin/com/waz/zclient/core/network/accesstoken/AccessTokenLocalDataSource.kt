package com.waz.zclient.core.network.accesstoken

import com.waz.zclient.storage.db.accountdata.AccessTokenEntity
import com.waz.zclient.storage.db.accountdata.ActiveAccountsDao
import com.waz.zclient.storage.pref.GlobalPreferences

class AccessTokenLocalDataSource(
    private val globalPreferences: GlobalPreferences,
    private val activeAccountsDao: ActiveAccountsDao
) {

    private val activeUserId get() = globalPreferences.activeUserId

    suspend fun accessToken(): AccessTokenEntity? = activeAccountsDao.accessToken(activeUserId)

    suspend fun updateAccessToken(newToken: AccessTokenEntity) =
        activeAccountsDao.updateAccessToken(activeUserId, newToken)

    suspend fun refreshToken(): String? = activeAccountsDao.refreshToken(activeUserId)

    suspend fun updateRefreshToken(newRefreshToken: String) =
        activeAccountsDao.updateRefreshToken(activeUserId, newRefreshToken)
}
