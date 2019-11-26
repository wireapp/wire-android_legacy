package com.waz.zclient.settings.presentation.ui.advanced

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.waz.zclient.R

class AdvancedFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_advanced, rootKey)
    }

    companion object {
        fun newInstance() = AdvancedFragment()
    }
}


