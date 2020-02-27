package com.waz.zclient.core.extension

import android.content.Context

import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.view.View
import android.view.inputmethod.InputMethodManager

val Context.networkInfo: NetworkInfo?
    get() = (this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo

fun Context.stringArrayFromResource(id: Int): Array<String> = resources.getStringArray(id)

