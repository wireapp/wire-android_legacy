package com.waz.zclient.settings.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.openUrl
import kotlinx.android.synthetic.main.fragment_settings_support.*
import org.koin.android.viewmodel.ext.android.viewModel

class SettingsSupportFragment : Fragment() {

    private val settingsSupportViewModel: SettingsSupportViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_support, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initViewModel()
        initSupportWebsiteButton()
        initSupportContactButton()
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

    private fun initViewModel() {
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
