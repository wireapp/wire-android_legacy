package com.waz.zclient.settings.ui.options

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.waz.zclient.BuildConfig
import com.waz.zclient.R
import com.waz.zclient.core.config.Config
import com.waz.zclient.core.extension.remove

class OptionsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.pref_options_screen_title)
    }

    companion object {
        fun newInstance() = OptionsFragment()
    }
}


