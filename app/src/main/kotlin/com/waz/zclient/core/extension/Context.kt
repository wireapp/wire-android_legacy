package com.waz.zclient.core.extension

import android.content.Context

fun Context.stringArrayFromResource(id: Int): Array<String> = resources.getStringArray(id)

fun Context.stringFromResource(id: Int): String = resources.getString(id)

