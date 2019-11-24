package com.waz.zclient.utilities.extension


import androidx.preference.Preference


fun Preference.remove() {
    parent?.removePreference(this)
}
