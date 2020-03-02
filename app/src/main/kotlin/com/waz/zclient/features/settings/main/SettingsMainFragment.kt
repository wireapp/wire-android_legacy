package com.waz.zclient.features.settings.main

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.core.navigation.navigator
import com.waz.zclient.core.ui.list.OnItemClickListener
import com.waz.zclient.features.settings.main.list.SettingsMainListAdapter
import com.waz.zclient.features.settings.main.list.SettingsMainListItemsFactory
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

    override fun onItemClicked(position: Int) = with(navigator()) {
        when (position) {
            ACCOUNT -> navigateTo(R.id.action_settingsMainFragment_to_settingsAccountFragment)
            DEVICES -> navigateTo(R.id.action_settingsMainFragment_to_settingsDeviceListFragment)
            OPTIONS -> navigateTo(R.id.action_settingsMainFragment_to_settingsOptionsFragment)
            ADVANCED -> navigateTo(R.id.action_settingsMainFragment_to_settingsAdvancedFragment)
            SUPPORT -> navigateTo(R.id.action_settingsMainFragment_to_settingsSupportFragment)
            ABOUT -> navigateTo(R.id.action_settingsMainFragment_to_settingsAboutFragment)
        }
    }

    companion object {
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
