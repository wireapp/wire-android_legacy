package com.waz.zclient.core.extension

import android.os.Looper

fun Thread.failFastIfUIThread() = require(this != Looper.getMainLooper().thread)
