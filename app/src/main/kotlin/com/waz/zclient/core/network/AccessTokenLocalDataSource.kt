package com.waz.zclient.core.network

import android.content.SharedPreferences
import com.google.gson.Gson
import com.waz.zclient.storage.extension.putString
import com.waz.zclient.storage.extension.remove
import com.waz.zclient.storage.extension.string

class AccessTokenLocalDataSource(private val userPreferences: SharedPreferences) {

    companion object {
        private const val KEY_ACCESS_TOKEN = "accessToken"
        private const val KEY_REFRESH_TOKEN = "refreshToken"
    }

    fun accessToken(): AccessTokenPreference? = readAccessToken()

    fun updateAccessToken(newToken: AccessTokenPreference) = writeAccessToken(newToken)

    fun refreshToken(): String? = userPreferences.string(KEY_REFRESH_TOKEN)

    fun updateRefreshToken(newRefreshToken: String) =
        userPreferences.putString(KEY_REFRESH_TOKEN, newRefreshToken)

    fun wipeOutAccessToken() = userPreferences.remove(KEY_ACCESS_TOKEN)

    fun wipeOutRefreshToken() = userPreferences.remove(KEY_REFRESH_TOKEN)

    private fun writeAccessToken(accessToken: AccessTokenPreference) = userPreferences.putString(
        KEY_ACCESS_TOKEN,
        Gson().toJson(accessToken, AccessTokenPreference::class.java)
    )

    private fun readAccessToken(): AccessTokenPreference? =
        userPreferences.getString(KEY_ACCESS_TOKEN, null)?.let {
            Gson().fromJson(it, AccessTokenPreference::class.java)
        }
}
