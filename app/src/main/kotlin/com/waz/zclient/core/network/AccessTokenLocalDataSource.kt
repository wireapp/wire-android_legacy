package com.waz.zclient.core.network

import android.content.SharedPreferences
import com.waz.zclient.storage.extension.putString
import com.waz.zclient.storage.extension.removeString
import com.waz.zclient.storage.extension.string

class AccessTokenLocalDataSource(private val userPreferences: SharedPreferences) {

    fun accessToken(): String? = userPreferences.string(KEY_ACCESS_TOKEN)

    fun updateAccessToken(newToken: String) =
        userPreferences.putString(KEY_ACCESS_TOKEN, newToken)

    fun refreshToken(): String? = userPreferences.string(KEY_REFRESH_TOKEN)

    fun updateRefreshToken(newRefreshToken: String) =
        userPreferences.putString(KEY_REFRESH_TOKEN, newRefreshToken)

    fun wipeOutAccessToken() = userPreferences.removeString(KEY_ACCESS_TOKEN)

    fun wipeOutRefreshToken() = userPreferences.removeString(KEY_REFRESH_TOKEN)

    companion object {
        private const val KEY_ACCESS_TOKEN = "accessToken"
        private const val KEY_REFRESH_TOKEN = "refreshToken"
    }

}
