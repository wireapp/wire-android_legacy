package com.waz.zclient.settings.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.waz.zclient.R
import com.waz.zclient.core.lists.OnItemClickListener
import com.waz.zclient.settings.ui.about.AboutFragment
import com.waz.zclient.settings.ui.account.AccountFragment
import com.waz.zclient.settings.ui.advanced.AdvancedFragment
import com.waz.zclient.settings.ui.home.list.SettingsListAdapter
import com.waz.zclient.settings.ui.home.list.SettingsListFactory
import com.waz.zclient.settings.ui.options.OptionsFragment
import com.waz.zclient.settings.ui.support.SupportFragment
import com.waz.zclient.utilities.extension.replaceFragment
import kotlinx.android.synthetic.main.fragment_settings.*

class SettingsFragment : Fragment(), OnItemClickListener {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.settings_title)
        settings_recycler_view.adapter = SettingsListAdapter(SettingsListFactory.generateList(requireContext()), this)

    }

    override fun onItemClicked(position: Int) {
        when (position) {
            ACCOUNT -> replaceFragment(AccountFragment.newInstance())
            OPTIONS -> replaceFragment(OptionsFragment.newInstance())
            ADVANCED -> replaceFragment(AdvancedFragment.newInstance())
            SUPPORT -> replaceFragment(SupportFragment.newInstance())
            ABOUT -> replaceFragment(AboutFragment.newInstance())
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        (activity as AppCompatActivity).replaceFragment(R.id.layout_container, fragment, true)
    }

    companion object {
        fun newInstance() = SettingsFragment()
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


