package com.waz.zclient.storage.pref

import android.content.Context
import android.content.SharedPreferences

class GlobalPreferences(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("com.wire.preferences", Context.MODE_PRIVATE)

    var activeUserId: String
        get() = getStringPrefs(ACTIVE_ACCOUNT_GLOBAL_PREF_KEY)
        set(value) = setStringPrefs(ACTIVE_ACCOUNT_GLOBAL_PREF_KEY, value)


    private fun setStringPrefs(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    private fun getStringPrefs(key: String): String {
        return sharedPreferences.getString(key, "")
    }

    companion object {

        private const val ACTIVE_ACCOUNT_GLOBAL_PREF_KEY = "active_account"

    }

}
