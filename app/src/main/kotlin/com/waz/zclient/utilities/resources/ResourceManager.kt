package com.waz.zclient.utilities.resources

import androidx.annotation.ArrayRes

interface ResourceManager {

    fun getStringArray(@ArrayRes array: Int): Array<String>
}
