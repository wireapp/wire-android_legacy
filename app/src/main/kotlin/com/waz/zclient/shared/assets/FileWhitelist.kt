package com.waz.zclient.shared.assets

import com.waz.zclient.BuildConfig
import java.util.Locale

class FileWhitelist(
     val extensions: Set<String> = BuildConfig.FILE_WHITELIST.split(",").map { it.trim().toLowerCase(Locale.ROOT) }.toSet()
) {
    fun isWhiteListed(fileName: String): Boolean =
        extensions.contains(fileName.split(".").last().trim().toLowerCase(Locale.ROOT))
}
