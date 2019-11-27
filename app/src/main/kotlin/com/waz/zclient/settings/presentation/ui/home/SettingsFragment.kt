package com.waz.zclient.settings.presentation.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.settings.presentation.ui.about.AboutFragment
import com.waz.zclient.settings.presentation.ui.account.AccountFragment
import com.waz.zclient.settings.presentation.ui.advanced.AdvancedFragment
import com.waz.zclient.settings.presentation.ui.devices.DevicesFragment
import com.waz.zclient.settings.presentation.ui.home.list.OnItemClickListener
import com.waz.zclient.settings.presentation.ui.home.list.SettingsListAdapter
import com.waz.zclient.settings.presentation.ui.home.list.SettingsListFactory
import com.waz.zclient.settings.presentation.ui.options.OptionsFragment
import com.waz.zclient.settings.presentation.ui.support.SupportFragment
import com.waz.zclient.utilities.extension.replaceFragment
import com.waz.zclient.utilities.resources.ResourceManagerImpl
import kotlinx.android.synthetic.main.fragment_settings.*

class SettingsFragment : Fragment(), OnItemClickListener {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.settings_title)
        val resourceManager = ResourceManagerImpl(resources)
        settings_recycler_view.adapter = SettingsListAdapter(SettingsListFactory.generateList(resourceManager), this)
    }

    override fun onItemClicked(position: Int) {

        when (position) {
            ACCOUNT -> replaceLayoutContainer(AccountFragment.newInstance())
            DEVICES -> replaceLayoutContainer(DevicesFragment.newInstance())
            OPTIONS -> replaceLayoutContainer(OptionsFragment.newInstance())
            ADVANCED -> replaceLayoutContainer(AdvancedFragment.newInstance())
            SUPPORT -> replaceLayoutContainer(SupportFragment.newInstance())
            ABOUT -> replaceLayoutContainer(AboutFragment.newInstance())
        }
    }

    fun replaceLayoutContainer(fragment: Fragment) {
        (activity as AppCompatActivity).replaceFragment(R.id.layout_container, fragment, true)
    }

    companion object {
        fun newInstance() = SettingsFragment()
        const val ACCOUNT = 0
        const val DEVICES = 1
        const val OPTIONS = 2
        const val ADVANCED = 3
        const val SUPPORT = 4
        const val ABOUT = 5
        const val DEVELOPER_SETTINGS = 6
        const val AVS_SETTINGS = 7
    }

}


