package com.waz.zclient.settings.presentation.ui.options

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.waz.zclient.R

class OptionsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_options, rootKey)
    }
    companion object {
        fun newInstance() = OptionsFragment()
    }
}


