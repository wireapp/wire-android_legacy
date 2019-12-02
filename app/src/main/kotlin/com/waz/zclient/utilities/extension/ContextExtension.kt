package com.waz.zclient.utilities.extension

import android.content.Context

fun Context.stringArrayFromResource(id: Int): Array<String> = resources.getStringArray(id)

fun Context.stringFromResource(id: Int): String = resources.getString(id)

