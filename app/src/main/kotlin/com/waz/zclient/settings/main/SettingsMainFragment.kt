package com.waz.zclient.settings.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.replaceFragment
import com.waz.zclient.core.lists.OnItemClickListener
import com.waz.zclient.settings.about.SettingsAboutFragment
import com.waz.zclient.settings.account.SettingsAccountFragment
import com.waz.zclient.settings.advanced.SettingsAdvancedFragment
import com.waz.zclient.settings.devices.list.SettingsDeviceListFragment
import com.waz.zclient.settings.main.list.SettingsMainListAdapter
import com.waz.zclient.settings.main.list.SettingsMainListFactory
import com.waz.zclient.settings.options.SettingsOptionsFragment
import com.waz.zclient.settings.support.SettingsSupportFragment
import kotlinx.android.synthetic.main.fragment_settings_main.*

class SettingsMainFragment : Fragment(), OnItemClickListener {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.settings_title)
        settings_recycler_view.adapter = SettingsMainListAdapter(SettingsMainListFactory.generateList(requireContext()), this)

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
        (activity as AppCompatActivity).replaceFragment(R.id.layout_container, fragment, true)
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


