package com.waz.zclient.core.extension

import org.json.JSONObject

fun JSONObject.replaceValue(key: String, value: Any?) {
    if (has(key)) {
        put(key, value)
    }
}
