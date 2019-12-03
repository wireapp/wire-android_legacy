package com.waz.zclient.utilities.extension


import androidx.preference.EditTextPreference
import androidx.preference.Preference


fun Preference.remove() {
    parent?.removePreference(this)
}

fun Preference.registerListener(onPreferenceChangeListener: Preference.OnPreferenceChangeListener) {
    setOnPreferenceChangeListener(onPreferenceChangeListener)
}

fun Preference.unRegisterListener() {
    onPreferenceChangeListener = null
}

fun EditTextPreference.titleAndText(value: String) {
    title = value
    text = value
}


