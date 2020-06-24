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
            extensions.contains(getExtension(fileName))
        else
            true

    private fun getExtension(fileName: String): String =
        fileName.split('.').last().trim().toLowerCase(Locale.ROOT)
}

