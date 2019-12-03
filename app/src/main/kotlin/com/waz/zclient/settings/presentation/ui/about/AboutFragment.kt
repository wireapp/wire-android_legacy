package com.waz.zclient.settings.presentation.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.utilities.config.Config
import com.waz.zclient.utilities.extension.openUrl
import kotlinx.android.synthetic.main.fragment_about.*

class AboutFragment : Fragment() {


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.pref_about_screen_title)
        preferences_about_website.setOnClickListener { openUrl(Config.websiteUrl()) }
        preferences_about_terms.setOnClickListener {}
        preferences_about_privacy.setOnClickListener { openUrl(getString(R.string.url_privacy_policy).replaceFirst(WEBSITE, Config.websiteUrl())) }
        preferences_about_license.setOnClickListener { openUrl(getString(R.string.url_third_party_licences).replaceFirst(WEBSITE, Config.websiteUrl())) }
        preferences_about_version.text = getString(R.string.pref_about_version_title, Config.versionName())
    }

    companion object {
        fun newInstance() = AboutFragment()
        const val WEBSITE = "|WEBSITE|"
    }
}


