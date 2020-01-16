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

    fun accessToken(): AccessTokenPreference? =
        readItem(KEY_ACCESS_TOKEN, AccessTokenPreference::class.java)

    fun updateAccessToken(newToken: AccessTokenPreference) =
        writeItem(KEY_ACCESS_TOKEN, newToken, AccessTokenPreference::class.java)

    fun refreshToken(): RefreshTokenPreference? =
        readItem(KEY_REFRESH_TOKEN, RefreshTokenPreference::class.java)

    fun updateRefreshToken(newRefreshToken: RefreshTokenPreference) =
        writeItem(KEY_REFRESH_TOKEN, newRefreshToken, RefreshTokenPreference::class.java)

    fun wipeOutAccessToken() = activeUserPrefs.remove(KEY_ACCESS_TOKEN)

    fun wipeOutRefreshToken() = activeUserPrefs.remove(KEY_REFRESH_TOKEN)

    //TODO: might move into a util
    private fun <T> writeItem(key: String, item: T, itemClass: Class<T>) =
        activeUserPrefs.putString(key, Gson().toJson(item, itemClass))

    private fun <T> readItem(key: String, itemClass: Class<T>): T? =
        activeUserPrefs.getString(key, null)?.let {
            Gson().fromJson(it, itemClass)
        }
}
