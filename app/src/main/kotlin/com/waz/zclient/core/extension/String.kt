package com.waz.zclient.core.extension

import android.os.Build
import android.text.Html
import android.util.Base64

fun String.Companion.empty() = ""

fun String.decodeBase64() =
    Base64.decode(this, Base64.DEFAULT)

fun String.toSpanned() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(this, Html.FROM_HTML_MODE_COMPACT)
    } else {
        Html.fromHtml(this)
    }
