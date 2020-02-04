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
        initToolbar()
        initViewModel()

        settingsAboutAboutWebsiteButton.setOnClickListener {
            settingsAboutViewModel.onAboutButtonClicked()
        }
        settingsAboutTermsAndConditionsButton.setOnClickListener {
            settingsAboutViewModel.onTermsButtonClicked()
        }
        settingsAboutPrivacyButton.setOnClickListener {
            settingsAboutViewModel.onPrivacyButtonClicked()
        }
        settingsAboutThirdPartyLicensesButton.setOnClickListener {
            settingsAboutViewModel.onThirdPartyLicenseButtonClicked()
        }

        settingsAboutAppVersionDetailsButton.text = getVersionName()
    }

    private fun initToolbar() {
        activity?.title = getString(R.string.pref_about_screen_title)
    }

    private fun initViewModel() {
        settingsAboutViewModel.urlLiveData.observe(viewLifecycleOwner) {
            openUrl(it.url)
        }
    }

    private fun getVersionName() = getString(R.string.pref_about_version_title, Config.versionName())

    companion object {
        fun newInstance() = SettingsAboutFragment()
    }
}
