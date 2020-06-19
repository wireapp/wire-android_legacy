package com.waz.zclient.shared.assets

import com.waz.zclient.BuildConfig
import java.util.*

class FileWhitelist {
    val enabled: Boolean
    val extensions: Set<String>

    constructor(): this(BuildConfig.ENABLE_FILE_WHITELIST, BuildConfig.FILE_WHITELIST)
    constructor(enabled: Boolean, listStr: String) {
        this.enabled = enabled
        this.extensions =
            if (enabled)
                listStr.split(",").map { it.trim().toLowerCase(Locale.ROOT) }.toSet()
            else
                emptySet()
    }

    fun isAllowed(fileName: String): Boolean =
        if (enabled)
            extensions.contains(fileName.split(".").last().trim().toLowerCase(Locale.ROOT))
        else
            true
}
