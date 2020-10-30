package com.waz.zclient.storage.pref.global

import android.content.Context
import android.content.SharedPreferences
import com.waz.zclient.storage.extension.putString
import com.waz.zclient.storage.extension.string

class GlobalPreferences(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_LOCATION_KEY, Context.MODE_PRIVATE)

    var activeUserId: String
        get() = sharedPreferences.string(ACTIVE_ACCOUNT_GLOBAL_PREF_KEY) ?: ""
        set(value) = sharedPreferences.putString(ACTIVE_ACCOUNT_GLOBAL_PREF_KEY, value)

    companion object {
        private const val PREFERENCES_LOCATION_KEY = "com.wire.preferences"
        private const val ACTIVE_ACCOUNT_GLOBAL_PREF_KEY = "active_account"
    }
}
