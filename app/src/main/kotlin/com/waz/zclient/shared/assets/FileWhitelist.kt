package com.waz.zclient.shared.assets

import com.waz.zclient.BuildConfig
import java.util.*

class FileWhitelist {
    private val enabled: Boolean
    private val list: List<String>

    constructor(enabled: Boolean, listStr: String) {
        this.enabled = enabled
        list = if (enabled) listStr.split(",").map { it.trim() } else emptyList()
    }

    constructor(): this(BuildConfig.ENABLE_FILE_WHITELIST, BuildConfig.FILE_WHITELIST)

    fun isAllowed(fileName: String): Boolean =
        if (enabled)
            list.contains(fileName.split(".").last().trim().toLowerCase(Locale.ROOT))
        else
            true
}