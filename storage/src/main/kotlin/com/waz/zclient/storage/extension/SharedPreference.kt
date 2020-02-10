package com.waz.zclient.storage.extension

import android.content.SharedPreferences

fun SharedPreferences.putString(key: String, value: String) = edit().putString(key, value).apply()

fun SharedPreferences.string(key: String, defValue: String = ""): String? = getString(key, defValue)

fun SharedPreferences.remove(key: String) = edit().remove(key).apply()
