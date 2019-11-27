package com.waz.zclient.utilities.resources

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes

interface ResourceManager {

    fun getStringArray(@ArrayRes id: Int): Array<String>
    fun getString(@StringRes id: Int): String
}
