package com.waz.zclient.core.extension


import androidx.preference.Preference


fun Preference.remove() {
    parent?.removePreference(this)
}
