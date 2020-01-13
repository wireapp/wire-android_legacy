package com.waz.zclient.core.network.accesstoken

import com.google.gson.Gson
import com.waz.zclient.storage.extension.putString
import com.waz.zclient.storage.extension.remove
import com.waz.zclient.storage.pref.UserPreferences

class AccessTokenLocalDataSource(private val userPreferences: UserPreferences) {

    companion object {
        private const val KEY_ACCESS_TOKEN = "accessToken"
        private const val KEY_REFRESH_TOKEN = "refreshToken"
    }

    private val activeUserPrefs get() = userPreferences.current()

    fun accessToken(): AccessTokenPreference? = readAccessToken()

    fun updateAccessToken(newToken: AccessTokenPreference) = writeAccessToken(newToken)

    fun refreshToken(): RefreshTokenPreference? = readRefreshToken()

    fun updateRefreshToken(newRefreshToken: RefreshTokenPreference) = writeRefreshToken(newRefreshToken)

    fun wipeOutAccessToken() = activeUserPrefs.remove(KEY_ACCESS_TOKEN)

    fun wipeOutRefreshToken() = activeUserPrefs.remove(KEY_REFRESH_TOKEN)

    private fun writeAccessToken(accessToken: AccessTokenPreference) = activeUserPrefs.putString(
        KEY_ACCESS_TOKEN,
        Gson().toJson(accessToken, AccessTokenPreference::class.java)
    )

    private fun readAccessToken(): AccessTokenPreference? =
        activeUserPrefs.getString(KEY_ACCESS_TOKEN, null)?.let {
            Gson().fromJson(it, AccessTokenPreference::class.java)
        }

    private fun writeRefreshToken(preference: RefreshTokenPreference) = activeUserPrefs.putString(
        KEY_REFRESH_TOKEN,
        Gson().toJson(preference, RefreshTokenPreference::class.java)
    )

    private fun readRefreshToken(): RefreshTokenPreference? =
        activeUserPrefs.getString(KEY_REFRESH_TOKEN, null)?.let {
            Gson().fromJson(it, RefreshTokenPreference::class.java)
        }
}
