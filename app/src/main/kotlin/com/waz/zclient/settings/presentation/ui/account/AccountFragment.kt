package com.waz.zclient.settings.presentation.ui.account

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.waz.zclient.BuildConfig
import com.waz.zclient.R
import com.waz.zclient.settings.data.model.UserEntity
import com.waz.zclient.settings.presentation.ui.SettingsViewModelFactory
import com.waz.zclient.settings.presentation.ui.home.list.SettingsListAdapter
import com.waz.zclient.settings.presentation.ui.home.list.SettingsListFactory
import com.waz.zclient.utilities.config.ConfigHelper
import com.waz.zclient.utilities.extension.remove
import com.waz.zclient.utilities.resources.ResourceManagerImpl
import kotlinx.android.synthetic.main.fragment_account.*
import kotlinx.android.synthetic.main.fragment_settings.*

class AccountFragment : Fragment() {

    private val settingsViewModelFactory: SettingsViewModelFactory by lazy { SettingsViewModelFactory() }
    private lateinit var settingsAccountViewModel : SettingsAccountViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_account, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsAccountViewModel = ViewModelProviders.of(this,settingsViewModelFactory).get(SettingsAccountViewModel::class.java)

        settingsAccountViewModel.getProfile()

        settingsAccountViewModel.profileUserData.observe(viewLifecycleOwner, Observer<UserEntity>{

            username.text = it.name
        })

    }

    companion object {
        fun newInstance() = AccountFragment()
    }
}


