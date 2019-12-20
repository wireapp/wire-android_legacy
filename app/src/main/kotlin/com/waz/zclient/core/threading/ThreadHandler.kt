package com.waz.zclient.core.threading

import android.os.Looper

class ThreadHandler {

    fun failFastIfUIThread() = require(!isUIThread())

    private fun isUIThread() = Thread.currentThread() == Looper.getMainLooper().thread
}
