package com.waz.zclient.settings.presentation.ui.support

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.waz.zclient.R

class SupportFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_support, rootKey)
    }

    companion object {
        fun newInstance() = SupportFragment()
    }
}


