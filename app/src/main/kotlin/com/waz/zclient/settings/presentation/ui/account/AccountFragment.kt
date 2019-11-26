package com.waz.zclient.settings.presentation.ui.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.waz.zclient.R
import com.waz.zclient.settings.presentation.ui.SettingsViewModelFactory
import com.waz.zclient.user.data.model.UserEntity
import com.waz.zclient.utilities.extension.remove


class AccountFragment : PreferenceFragmentCompat() {

    private val settingsViewModelFactory: SettingsViewModelFactory by lazy { SettingsViewModelFactory() }
    private lateinit var settingsAccountViewModel: SettingsAccountViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_account, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsAccountViewModel = ViewModelProviders.of(this,settingsViewModelFactory).get(SettingsAccountViewModel::class.java)
        settingsAccountViewModel.getProfile()
        settingsAccountViewModel.profileUserData.observe(viewLifecycleOwner, Observer<UserEntity>{

            val prefName: Preference? = findPreference(resources.getString(R.string.pref_key_name))
            prefName?.title = it.name

            val prefUserName: Preference? = findPreference(resources.getString(R.string.pref_key_username))
            prefUserName?.title = it.handle

            val prefEmail: Preference? = findPreference(resources.getString(R.string.pref_key_email))
            prefEmail?.title = it.email

            val prefPhone: Preference? = findPreference(resources.getString(R.string.pref_key_phone))
            prefPhone?.title = it.phone
        })
    }
    companion object {
        fun newInstance() = AccountFragment()
    }
}


