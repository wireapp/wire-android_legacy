package com.waz.zclient.storage.pref.backend

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.waz.zclient.storage.extension.empty
import com.waz.zclient.storage.extension.putString
import com.waz.zclient.storage.extension.string

class BackendPreferences(context: Context) {

    private val sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    var environment: String
        get() = sharedPreferences.string(ENVIRONMENT_PREF) ?: String.empty()
        set(value) = sharedPreferences.putString(ENVIRONMENT_PREF, value)

    var baseUrl: String
        get() = sharedPreferences.string(BASE_URL_PREF) ?: String.empty()
        set(value) = sharedPreferences.putString(BASE_URL_PREF, value)

    var customConfigUrl: String
        get() = sharedPreferences.string(CONFIG_URL_PREF) ?: String.empty()
        set(value) = sharedPreferences.putString(CONFIG_URL_PREF, value)

    var websocketUrl: String
        get() = sharedPreferences.string(WEBSOCKET_URL_PREF) ?: String.empty()
        set(value) = sharedPreferences.putString(WEBSOCKET_URL_PREF, value)

    var blacklistUrl: String
        get() = sharedPreferences.string(BLACKLIST_HOST_PREF) ?: String.empty()
        set(value) = sharedPreferences.putString(BLACKLIST_HOST_PREF, value)

    var teamsUrl: String
        get() = sharedPreferences.string(TEAMS_URL_PREF) ?: String.empty()
        set(value) = sharedPreferences.putString(TEAMS_URL_PREF, value)

    var accountsUrl: String
        get() = sharedPreferences.string(ACCOUNTS_URL_PREF) ?: String.empty()
        set(value) = sharedPreferences.putString(ACCOUNTS_URL_PREF, value)

    var websiteUrl: String
        get() = sharedPreferences.string(WEBSITE_URL_PREF) ?: String.empty()
        set(value) = sharedPreferences.putString(WEBSITE_URL_PREF, value)

    companion object {
        private const val ENVIRONMENT_PREF = "CUSTOM_BACKEND_ENVIRONMENT"
        private const val BASE_URL_PREF = "CUSTOM_BACKEND_BASE_URL"
        private const val WEBSOCKET_URL_PREF = "CUSTOM_BACKEND_WEBSOCKET_URL"
        private const val BLACKLIST_HOST_PREF = "CUSTOM_BACKEND_BLACKLIST_HOST"
        private const val TEAMS_URL_PREF = "CUSTOM_BACKEND_TEAMS_URL"
        private const val ACCOUNTS_URL_PREF = "CUSTOM_BACKEND_ACCOUNTS_URL"
        private const val WEBSITE_URL_PREF = "CUSTOM_BACKEND_WEBSITE_URL"
        private const val CONFIG_URL_PREF = "CUSTOM_BACKEND_CONFIG_URL"
    }
}
