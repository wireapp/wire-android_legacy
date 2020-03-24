package com.waz.zclient.core.extension

import android.util.Base64

fun ByteArray.encodeBase64() =
    Base64.encodeToString(this, Base64.NO_WRAP or Base64.NO_CLOSE)
