package com.waz.zclient.settings.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.config.Config
import com.waz.zclient.core.extension.openUrl
import com.waz.zclient.settings.account.model.UserProfileItem
import kotlinx.android.synthetic.main.fragment_settings_account.*
import org.koin.android.viewmodel.ext.android.viewModel


class SettingsAccountFragment : Fragment() {

    private val settingsAccountViewModel: SettingsAccountViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initViewModel()
        setupListeners()
        loadData()
    }

    private fun initToolbar() {
        activity?.title = getString(R.string.pref_account_screen_title)
    }

    private fun initViewModel() {
        with(settingsAccountViewModel) {
            loading.observe(viewLifecycleOwner) { isLoading ->
                updateLoadingVisibility(isLoading)
            }
            error.observe(viewLifecycleOwner) { errorMessage ->
                showErrorMessage(errorMessage)
            }
            profile.observe(viewLifecycleOwner) { profile ->
                updateProfile(profile)
            }

        }
    }

    private fun setupListeners() {
        preferences_account_reset_password.setOnClickListener { openUrl(getString(R.string.url_password_forgot).replaceFirst(Accounts, Config.accountsUrl())) }
    }

    private fun loadData() {
        lifecycleScope.launchWhenResumed {
            settingsAccountViewModel.loadData()
        }
    }

    private fun showErrorMessage(errorMessage: String) {
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun updateLoadingVisibility(isLoading: Boolean?) {
        //Show hide progress indicator
    }

    private fun updateProfile(userProfileItem: UserProfileItem) {
        preferences_account_name_title.text = userProfileItem.name
        preferences_account_email_title.text = userProfileItem.email
        preferences_account_handle_title.text = userProfileItem.handle
        preferences_account_phone_title.text = userProfileItem.phone
    }

    companion object {
        fun newInstance() = SettingsAccountFragment()
        private const val Accounts = "|ACCOUNTS|"
    }


}


