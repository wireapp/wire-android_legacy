package com.waz.zclient.feature.settings.options

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R

class SettingsOptionsFragment : Fragment(R.layout.fragment_settings_options) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.pref_options_screen_title)
    }

    companion object {
        fun newInstance() = SettingsOptionsFragment()
    }
}
