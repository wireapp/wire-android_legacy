package com.waz.zclient.core.extension

import android.os.Build
import android.text.Html

fun String.Companion.empty() = ""

fun String.toSpanned() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(this, Html.FROM_HTML_MODE_COMPACT)
    } else {
        Html.fromHtml(this)
    }
