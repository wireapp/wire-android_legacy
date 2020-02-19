package com.waz.zclient.settings.about

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.config.Config
import com.waz.zclient.core.extension.getDeviceLocale
import com.waz.zclient.core.extension.openUrl
import kotlinx.android.synthetic.main.fragment_settings_about.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.android.viewmodel.ext.android.viewModel

@ExperimentalCoroutinesApi
class SettingsAboutFragment : Fragment(R.layout.fragment_settings_about) {

    private val settingsAboutViewModel: SettingsAboutViewModel by viewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initViewModel()
        initAboutWebsiteButton()
        initTermsAndConditionsButton()
        initPrivacyButton()
        initLicensesButton()
        initAppVersionDetailsButton()
    }

    private fun initAppVersionDetailsButton() {
        settingsAboutAppVersionDetailsButton.text = getVersionName()
        settingsAboutAppVersionDetailsButton.setOnClickListener {
            settingsAboutViewModel.onVersionButtonClicked()
        }
    }

    private fun initLicensesButton() {
        settingsAboutThirdPartyLicensesButton.setOnClickListener {
            settingsAboutViewModel.onThirdPartyLicenseButtonClicked()
        }
    }

    private fun initPrivacyButton() {
        settingsAboutPrivacyButton.setOnClickListener {
            settingsAboutViewModel.onPrivacyButtonClicked()
        }
    }

    private fun initTermsAndConditionsButton() {
        settingsAboutTermsAndConditionsButton.setOnClickListener {
            settingsAboutViewModel.onTermsButtonClicked()
        }
    }

    private fun initAboutWebsiteButton() {
        settingsAboutAboutWebsiteButton.setOnClickListener {
            settingsAboutViewModel.onAboutButtonClicked()
        }
    }

    private fun initToolbar() {
        activity?.title = getString(R.string.pref_about_screen_title)
    }

    private fun initViewModel() {
        settingsAboutViewModel.urlLiveData.observe(viewLifecycleOwner) {
            openUrl(it.url)
        }

        settingsAboutViewModel.versionDetailsLiveData.observe(viewLifecycleOwner) {
            val translationId = resources.getIdentifier(
                it.translationsVersionId,
                "string",
                requireContext().packageName
            )
            val translationLibVersion = if (translationId == 0) "n/a" else getString(translationId)
            val avsVersion = getString(it.avsVersionRes)
            val audioNotificationVersion = getString(it.audioNotificationVersionRes)

            val versionToast = """
                Version: ${it.appVersionDetails}
                AVS: $avsVersion
                Audio-notifications: $audioNotificationVersion
                Translations: $translationLibVersion
                Locale: ${requireActivity().getDeviceLocale()}
            """.trimIndent()

            Toast.makeText(requireContext(), versionToast, Toast.LENGTH_LONG).show()
        }
    }

    private fun getVersionName() = getString(R.string.pref_about_version_title, Config.versionName())

    companion object {
        fun newInstance() = SettingsAboutFragment()
    }
}
