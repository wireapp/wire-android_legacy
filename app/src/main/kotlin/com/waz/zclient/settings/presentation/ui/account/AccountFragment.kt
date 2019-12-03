package com.waz.zclient.settings.presentation.ui.account

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceFragmentCompat
import com.waz.zclient.R
import com.waz.zclient.core.resources.Resource
import com.waz.zclient.core.resources.ResourceStatus
import com.waz.zclient.settings.presentation.model.UserItem
import com.waz.zclient.settings.presentation.ui.devices.SettingsDeviceViewModelFactory
import com.waz.zclient.utilities.extension.registerListener
import com.waz.zclient.utilities.extension.titleAndText
import com.waz.zclient.utilities.extension.unRegisterListener


class AccountFragment : PreferenceFragmentCompat(), OnPreferenceChangeListener {

    private val settingsDeviceViewModelFactory: SettingsDeviceViewModelFactory by lazy { SettingsDeviceViewModelFactory() }
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

        namePreference.registerListener(this)
        handlePreference.registerListener(this)
        emailPreference.registerListener(this)
        phonePreference.registerListener(this)

        settingsAccountViewModel = ViewModelProviders.of(this, settingsDeviceViewModelFactory).get(SettingsAccountViewModel::class.java)
        settingsAccountViewModel.getProfile()

        settingsAccountViewModel.profileUserData.observe(viewLifecycleOwner, Observer<Resource<UserItem>> {

            updateUi(it)
        })

    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {

        val value = newValue.toString()
        (preference as EditTextPreference).titleAndText(value)
        when (preference) {
            namePreference -> settingsAccountViewModel.updateName(value)
            handlePreference -> settingsAccountViewModel.updateHandle(value)
            handlePreference -> settingsAccountViewModel.updatePhone(value)
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        namePreference.unRegisterListener()
        handlePreference.unRegisterListener()
        emailPreference.unRegisterListener()
        phonePreference.unRegisterListener()
    }

    private fun updateUi(resource: Resource<UserItem>) {
        when (resource.status) {
            ResourceStatus.SUCCESS -> {
                resource.data?.name?.let { name -> namePreference.titleAndText(name) }
                resource.data?.handle?.let { handle -> handlePreference.titleAndText(handle) }
                resource.data?.email?.let { email -> emailPreference.titleAndText(email) }
                resource.data?.phone?.let { phone -> phonePreference.titleAndText(phone) }
            }
            ResourceStatus.ERROR -> {
                Toast.makeText(activity, resource.message, Toast.LENGTH_LONG).show()
            }
        }


    }

    companion object {
        fun newInstance() = AccountFragment()
    }


}


