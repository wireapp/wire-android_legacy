package com.waz.zclient.settings.presentation.ui.about

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.waz.zclient.R

class AboutFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_about, rootKey)
    }

    companion object {
        fun newInstance() = AboutFragment()
    }
}


