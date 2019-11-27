package com.waz.zclient.settings.presentation.ui.account

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.waz.zclient.R
import com.waz.zclient.settings.presentation.model.UserItem
import com.waz.zclient.settings.presentation.ui.SettingsViewModelFactory
import com.waz.zclient.user.data.model.UserEntity
import com.waz.zclient.utilities.resources.ResourceManager
import com.waz.zclient.utilities.resources.ResourceManagerImpl


class AccountFragment : PreferenceFragmentCompat() , SharedPreferences.OnSharedPreferenceChangeListener{

    private val settingsViewModelFactory: SettingsViewModelFactory by lazy { SettingsViewModelFactory() }
    private lateinit var settingsAccountViewModel: SettingsAccountViewModel

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_account, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.title = getString(R.string.pref_account_screen_title)

        settingsAccountViewModel = ViewModelProviders.of(this, settingsViewModelFactory).get(SettingsAccountViewModel::class.java)
        settingsAccountViewModel.getProfile()
        settingsAccountViewModel.profileUserData.observe(viewLifecycleOwner, Observer<UserItem> {
            updateEditTextPreference(getString(R.string.pref_key_name),it.name)
            updateEditTextPreference(getString(R.string.pref_key_username),it.handle)
            updateEditTextPreference(getString(R.string.pref_key_email),it.email)
            updateEditTextPreference(getString(R.string.pref_key_phone),it.phone)
        })

        PreferenceManager.getDefaultSharedPreferences(activity).registerOnSharedPreferenceChangeListener(this)
    }


    private fun updateEditTextPreference(key : String, value : String){
        val editTextPreference: EditTextPreference? = findPreference(key)
        editTextPreference?.title = value
        editTextPreference?.text =  value
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val editTextPreference: EditTextPreference? = findPreference(key.toString())
        editTextPreference?.title = editTextPreference?.text

       /* when(key){
            getString(R.string.pref_key_name) ->
                getString(R.string.pref_key_username) ->
            getString(R.string.pref_key_email) ->
            getString(R.string.pref_key_phone) ->
        }*/
    }

    override fun onDestroyView() {
        super.onDestroyView()
        PreferenceManager.getDefaultSharedPreferences(activity).unregisterOnSharedPreferenceChangeListener(this)
    }

    companion object {
        fun newInstance() = AccountFragment()
    }


}


