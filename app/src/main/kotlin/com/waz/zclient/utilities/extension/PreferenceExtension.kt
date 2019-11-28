package com.waz.zclient.utilities.extension


import androidx.preference.EditTextPreference
import androidx.preference.Preference


fun Preference.remove() {
    parent?.removePreference(this)
}

fun EditTextPreference.forceValue(value:String) {
    title = value
    text = value
}


