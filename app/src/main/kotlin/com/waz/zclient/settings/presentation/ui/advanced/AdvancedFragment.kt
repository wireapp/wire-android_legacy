package com.waz.zclient.settings.presentation.ui.advanced

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import com.waz.zclient.R

class AdvancedFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_advanced, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.pref_advanced_screen_title)
    }

    companion object {
        fun newInstance() = AdvancedFragment()
    }
}


