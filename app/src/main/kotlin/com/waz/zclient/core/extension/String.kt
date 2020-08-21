package com.waz.zclient.core.extension

import android.text.Html

fun String.Companion.empty() = ""

fun String.toSpanned() = Html.fromHtml(this, Html.FROM_HTML_MODE_COMPACT)
