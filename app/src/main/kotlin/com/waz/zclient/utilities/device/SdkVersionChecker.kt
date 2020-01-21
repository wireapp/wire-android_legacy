package com.waz.zclient.utilities.device

import android.os.Build

class SdkVersionChecker {

    fun isAndroid6orAbove() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
}
