package com.waz.zclient.feature.settings.main

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.ui.list.OnItemClickListener
import com.waz.zclient.feature.settings.about.SettingsAboutFragment
import com.waz.zclient.feature.settings.account.SettingsAccountFragment
import com.waz.zclient.feature.settings.advanced.SettingsAdvancedFragment
import com.waz.zclient.feature.settings.devices.list.SettingsDeviceListFragment
import com.waz.zclient.feature.settings.main.list.SettingsMainListAdapter
import com.waz.zclient.feature.settings.main.list.SettingsMainListItemsFactory
import com.waz.zclient.feature.settings.options.SettingsOptionsFragment
import com.waz.zclient.feature.settings.support.SettingsSupportFragment
import kotlinx.android.synthetic.main.fragment_settings_main.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

@ExperimentalCoroutinesApi
@InternalCoroutinesApi
class SettingsMainFragment : Fragment(R.layout.fragment_settings_main), OnItemClickListener {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.settings_title)
        settingsMainRecyclerView.adapter = SettingsMainListAdapter(
            SettingsMainListItemsFactory.generateList(requireContext()), this
        )
    }

    override fun onItemClicked(position: Int) {
        when (position) {
            ACCOUNT -> replaceFragment(SettingsAccountFragment.newInstance())
            DEVICES -> replaceFragment(SettingsDeviceListFragment.newInstance())
            OPTIONS -> replaceFragment(SettingsOptionsFragment.newInstance())
            ADVANCED -> replaceFragment(SettingsAdvancedFragment.newInstance())
            SUPPORT -> replaceFragment(SettingsSupportFragment.newInstance())
            ABOUT -> replaceFragment(SettingsAboutFragment.newInstance())
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        (activity as AppCompatActivity).replaceFragment(R.id.activitySettingsMainLayoutContainer, fragment)
    }

    companion object {
        fun newInstance() = SettingsMainFragment()
        private const val ACCOUNT = 0
        private const val DEVICES = 1
        private const val OPTIONS = 2
        private const val ADVANCED = 3
        private const val SUPPORT = 4
        private const val ABOUT = 5
        private const val DEVELOPER_SETTINGS = 6
        private const val AVS_SETTINGS = 7
    }
}
