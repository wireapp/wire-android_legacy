package com.waz.service.assets

import java.util.Locale

class FileRestrictionList(
        val extensions: Set<String>,
        val enabled: Boolean
) {
    constructor(extensions: String, enabled: Boolean):
        this(
            if (enabled)
                extensions.split(',').map { it.trim().removePrefix(".").toLowerCase(Locale.ROOT) }.toSet()
            else
                emptySet<String>(),
            enabled
        )

    fun isAllowed(fileName: String): Boolean =
        if (enabled)
            extensions.contains(fileName.split('.').last().trim().toLowerCase(Locale.ROOT))
        else
            true
}

