package com.waz.zclient.settings.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.openUrl
import kotlinx.android.synthetic.main.fragment_settings_support.*

class SettingsSupportFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_support, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.pref_support_screen_title)
        settings_support_website.setOnClickListener { openUrl(getString(R.string.url_support_website)) }
        settings_support_contact.setOnClickListener { openUrl(getString(R.string.url_contact_support)) }
    }

    companion object {
        fun newInstance() = SettingsSupportFragment()
    }
}


