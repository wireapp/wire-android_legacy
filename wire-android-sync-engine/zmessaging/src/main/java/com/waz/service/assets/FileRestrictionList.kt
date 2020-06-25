package com.waz.service.assets

import java.util.Locale

class FileRestrictionList(
        val extensions: Set<String>,
        val enabled: Boolean
) {
    companion object {
        private fun parseExtensions(extensions: String, enabled: Boolean): Set<String> =
            if (enabled) extensions.split(',').map { it.trim().removePrefix(".").toLowerCase(Locale.ROOT) }.toSet()
            else emptySet()
    }

    constructor(extensions: String, enabled: Boolean): this(parseExtensions(extensions, enabled), enabled)

    fun isAllowed(fileName: String): Boolean =
        if (enabled) extensions.contains(fileName.split('.').last().trim().toLowerCase(Locale.ROOT))
        else true
}

