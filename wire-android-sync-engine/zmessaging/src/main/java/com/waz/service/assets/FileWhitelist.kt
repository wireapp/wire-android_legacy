package com.waz.service.assets

import java.util.Locale

class FileWhitelist(
        val extensions: Set<String>,
        val enabled: Boolean
) {
    constructor(extensions: String, enabled: Boolean):
        this(
            extensions.split(",").map {
                it.trim().removePrefix(".").toLowerCase(Locale.ROOT)
            }.toSet(),
            enabled
        )

    fun isWhiteListed(fileName: String): Boolean =
        if (!enabled)
            true
        else
            extensions.contains(fileName.split(".").last().trim().toLowerCase(Locale.ROOT))
}

