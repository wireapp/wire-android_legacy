package com.waz.zclient.core.extension

import android.text.Html

fun String.Companion.empty() = ""

fun String.toSpanned() = Html.fromHtml(this, Html.FROM_HTML_MODE_COMPACT)

fun ByteArray.describe(splitAt: Int = 4): String {
    val contents =
        if (size <= splitAt * 2) contentToString()
        else {
            val head = take(splitAt).map { it.toInt() }.joinToString(", ")
            val tail = drop(size - splitAt).map { it.toInt() }.joinToString(", ")
            "[$head, ..., $tail]"
        }
    return "$contents ($size)"
}
