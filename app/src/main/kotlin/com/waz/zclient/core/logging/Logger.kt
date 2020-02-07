package com.waz.zclient.core.logging

import android.util.Log
import com.waz.zclient.core.extension.empty

class Logger private constructor() {
    companion object {
        @JvmStatic
        fun warn(tag: String, log: String) = Log.w(tag, log)

        @JvmStatic
        fun warn(tag: String, log: String, throwable: Throwable) = Log.w(tag, log, throwable)

        @JvmStatic
        fun info(tag: String, log: String) = Log.i(tag, log)

        @JvmStatic
        fun verbose(tag: String, log: String) = Log.v(tag, log)

        @JvmStatic
        fun debug(tag: String, log: String) = Log.d(tag, log)

        @JvmStatic
        fun error(tag: String, log: String) = Log.e(tag, log)

        @JvmStatic
        fun error(tag: String, log: String = String.empty(), throwable: Throwable) = Log.e(tag, log, throwable)
    }
}
