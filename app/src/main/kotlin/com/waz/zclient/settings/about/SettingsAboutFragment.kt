package com.waz.zclient.settings.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.config.Config
import com.waz.zclient.core.extension.openUrl
import kotlinx.android.synthetic.main.fragment_settings_about.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.viewmodel.ext.android.viewModel

@ExperimentalCoroutinesApi
class SettingsAboutFragment : Fragment() {

    private val settingsAboutViewModel: SettingsAboutViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.pref_about_screen_title)
        initViewModel()
        preferences_about_website.setOnClickListener { settingsAboutViewModel.onAboutButtonClicked() }
        preferences_about_terms.setOnClickListener { settingsAboutViewModel.onTermsButtonClicked() }
        preferences_about_privacy.setOnClickListener { settingsAboutViewModel.onPrivacyButtonClicked() }
        preferences_about_license.setOnClickListener { settingsAboutViewModel.onThirdPartyLicenseButtonClicked() }
        preferences_about_version.text = getString(R.string.pref_about_version_title, Config.versionName())
    }

    private fun initViewModel() {
        settingsAboutViewModel.urlLiveData.observe(viewLifecycleOwner) {
            when (it) {
                UrlDetail.EMPTY -> startUrl(Config.websiteUrl())
                else -> startUrl("${Config.websiteUrl()}${it.urlSuffix}")
            }
        }
    }

    private fun startUrl(url: String) {
        openUrl(url)
    }

    companion object {
        fun newInstance() = SettingsAboutFragment()
    }
}
