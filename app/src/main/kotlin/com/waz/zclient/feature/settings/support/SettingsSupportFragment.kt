package com.waz.zclient.feature.settings.support

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.openUrl
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.feature.settings.di.SETTINGS_SCOPE_ID
import kotlinx.android.synthetic.main.fragment_settings_support.*

class SettingsSupportFragment : Fragment(R.layout.fragment_settings_support) {

    private val settingsSupportViewModel by viewModel<SettingsSupportViewModel>(SETTINGS_SCOPE_ID)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initSupportWebsiteButton()
        initSupportContactButton()
        observeUrlData()
    }

    private fun initSupportContactButton() {
        settingsSupportContactButton.setOnClickListener {
            settingsSupportViewModel.onSupportContactClicked()
        }
    }

    private fun initSupportWebsiteButton() {
        settingsSupportWebsiteButton.setOnClickListener {
            settingsSupportViewModel.onSupportWebsiteClicked()
        }
    }

    private fun observeUrlData() {
        settingsSupportViewModel.urlLiveData.observe(viewLifecycleOwner) {
            openUrl(it.url)
        }
    }

    private fun initToolbar() {
        activity?.title = getString(R.string.pref_support_screen_title)
    }

    companion object {
        fun newInstance() = SettingsSupportFragment()
    }
}
