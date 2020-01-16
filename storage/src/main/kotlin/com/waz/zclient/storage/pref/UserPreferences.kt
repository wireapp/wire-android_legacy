package com.waz.zclient.storage.pref

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(private val context: Context,
                      private val globalPreferences: GlobalPreferences) {

    private fun getPrefFileName(userId: String) = "userPref_$userId"

    fun get(userId: String): SharedPreferences =
        context.getSharedPreferences(getPrefFileName(userId), Context.MODE_PRIVATE)

    fun current(): SharedPreferences = get(globalPreferences.activeUserId)
}
