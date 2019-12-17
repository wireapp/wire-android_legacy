package com.waz.zclient.storage.extension

import android.content.SharedPreferences

fun SharedPreferences.string(key: String, value: String) {
    edit().putString(key, value).apply()
}

fun SharedPreferences.string(key: String): String {
    return getString(key, "")
}
