package com.waz.zclient.settings.presentation.ui.account

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.waz.zclient.R
import com.waz.zclient.settings.presentation.model.UserItem
import com.waz.zclient.settings.presentation.ui.SettingsViewModelFactory
import com.waz.zclient.utilities.extension.forceValue


class AccountFragment : PreferenceFragmentCompat(), OnPreferenceChangeListener {

    private val settingsViewModelFactory: SettingsViewModelFactory by lazy { SettingsViewModelFactory() }
    private lateinit var settingsAccountViewModel: SettingsAccountViewModel

    private lateinit var namePreference: EditTextPreference
    private lateinit var handlePreference: EditTextPreference
    private lateinit var emailPreference: EditTextPreference
    private lateinit var phonePreference: EditTextPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_account, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.title = getString(R.string.pref_account_screen_title)

        namePreference = findPreference(getString(R.string.pref_key_name))!!
        handlePreference = findPreference(getString(R.string.pref_key_username))!!
        emailPreference = findPreference(getString(R.string.pref_key_email))!!
        phonePreference = findPreference(getString(R.string.pref_key_phone))!!

        namePreference.onPreferenceChangeListener = this
        handlePreference.onPreferenceChangeListener = this
        emailPreference.onPreferenceChangeListener = this
        phonePreference.onPreferenceChangeListener = this

        settingsAccountViewModel = ViewModelProviders.of(this, settingsViewModelFactory).get(SettingsAccountViewModel::class.java)
        settingsAccountViewModel.getProfile()

        settingsAccountViewModel.profileUserData.observe(viewLifecycleOwner, Observer<UserItem> {
            namePreference.forceValue(it.name)
            handlePreference.forceValue(it.handle)
            emailPreference.forceValue(it.email)
            phonePreference.forceValue(it.phone)
        })

    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {

        val value = newValue.toString()
        (preference  as EditTextPreference).forceValue(value)
        when(preference){
            namePreference -> settingsAccountViewModel.updateName(value)
            handlePreference -> settingsAccountViewModel.updateHandle(value)
            handlePreference -> settingsAccountViewModel.updatePhone(value)
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        namePreference.onPreferenceChangeListener = null
        handlePreference.onPreferenceChangeListener = null
        emailPreference.onPreferenceChangeListener = null
        phonePreference.onPreferenceChangeListener = null
    }

    companion object {
        fun newInstance() = AccountFragment()
    }




}


