package com.waz.zclient.core.utilities.base64

import android.util.Base64

class Base64Transformer {
    fun encode(input: ByteArray): String =
        Base64.encodeToString(input, Base64.NO_WRAP or Base64.NO_CLOSE)

    fun decode(input: String): ByteArray =
        Base64.decode(input, Base64.NO_WRAP or Base64.NO_CLOSE)
}
