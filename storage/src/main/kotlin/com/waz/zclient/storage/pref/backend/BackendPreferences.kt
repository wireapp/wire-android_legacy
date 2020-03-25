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

    companion object {
        const val ENVIRONMENT_PREF = "CUSTOM_BACKEND_ENVIRONMENT"
    }
}
