package com.waz.zclient.settings.presentation.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.waz.zclient.R
import com.waz.zclient.core.resources.Resource
import com.waz.zclient.settings.presentation.model.UserItem
import com.waz.zclient.settings.presentation.ui.SettingsViewModelFactory
import kotlinx.android.synthetic.main.fragment_account.*


class AccountFragment : Fragment() {

    private val settingsViewModelFactory: SettingsViewModelFactory by lazy { SettingsViewModelFactory() }
    private lateinit var settingsAccountViewModel: SettingsAccountViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.pref_account_screen_title)
        settingsAccountViewModel = ViewModelProviders.of(this, settingsViewModelFactory).get(SettingsAccountViewModel::class.java)
        settingsAccountViewModel.getProfile()
        settingsAccountViewModel.profileUserData.observe(viewLifecycleOwner, Observer<Resource<UserItem>> {
            preferences_account_name.text = it.data?.name
            preferences_account_email.text = it.data?.email
            preferences_account_handle.text = it.data?.handle
        })
    }

    companion object {
        fun newInstance() = AccountFragment()
    }


}


