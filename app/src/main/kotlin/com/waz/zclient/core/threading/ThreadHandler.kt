package com.waz.zclient.core.threading

import android.os.Looper

class ThreadHandler {
    fun isUIThread() = Thread.currentThread() == Looper.getMainLooper().thread
}
