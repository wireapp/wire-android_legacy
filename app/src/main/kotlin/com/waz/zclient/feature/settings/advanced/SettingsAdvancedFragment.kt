package com.waz.zclient.feature.settings.advanced

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R

class SettingsAdvancedFragment : Fragment(R.layout.fragment_settings_advanced) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.pref_advanced_screen_title)
    }

    companion object {
        fun newInstance() = SettingsAdvancedFragment()
    }
}
