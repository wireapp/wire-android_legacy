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
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.openUrl
import com.waz.zclient.core.ui.dialog.EditTextDialogFragment
import kotlinx.android.synthetic.main.fragment_settings_account.*
import org.koin.android.viewmodel.ext.android.viewModel


class SettingsAccountFragment : Fragment(), EditTextDialogFragment.EditTextDialogFragmentListener {

    private val settingsAccountViewModel: SettingsAccountViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initViewModel()
        initAccountName()
        initResetPassword()
        loadProfile()
    }

    private fun initAccountName() {
        preferences_account_name.setOnClickListener { showEditNameDialogFragment() }
    }

    private fun initResetPassword() {
        preferences_account_reset_password.setOnClickListener { openUrl(getString(R.string.url_password_forgot).replaceFirst(Accounts, Config.accountsUrl())) }
    }

    override fun onTextEdited(newValue: String) {
        settingsAccountViewModel.updateName(newValue)
    }

    private fun initToolbar() {
        activity?.title = getString(R.string.pref_account_screen_title)
    }

    private fun initViewModel() {
        with(settingsAccountViewModel) {
            error.observe(viewLifecycleOwner) { errorMessage ->
                showErrorMessage(errorMessage)
            }
            name.observe(viewLifecycleOwner) { name ->
                updateAccountName(name)
            }
            handle.observe(viewLifecycleOwner) { handle ->
                updateAccountHandle(handle)
            }
            email.observe(viewLifecycleOwner) { emailState ->
                updateAccountEmail(emailState)
            }
            phone.observe(viewLifecycleOwner) { phoneState ->
                updateAccountPhoneNumber(phoneState)
            }
            nameUpdated.observe(viewLifecycleOwner) {
                this@SettingsAccountFragment.loadProfile()
            }

        }
    }

    private fun updateAccountHandle(handle: String) {
        preferences_account_handle_title.text = handle
    }

    private fun updateAccountName(name: String) {
        preferences_account_handle_title.text = name
    }

    private fun updateAccountPhoneNumber(phoneState: ProfileDetailsState) {
        when (phoneState) {
            is ProfileDetailNull -> preferences_account_phone_title.text = getString(R.string.pref_account_add_email_title)
            is ProfileDetail -> preferences_account_phone_title.text = phoneState.value
        }
    }

    private fun updateAccountEmail(emailState: ProfileDetailsState) {
        when (emailState) {
            is ProfileDetailNull -> preferences_account_email_title.text = getString(R.string.pref_account_add_email_title)
            is ProfileDetail -> preferences_account_email_title.text = emailState.value
        }
    }

    private fun loadProfile() {
        lifecycleScope.launchWhenResumed {
            settingsAccountViewModel.loadProfile()
        }
    }

    private fun showErrorMessage(errorMessage: String) {
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun showEditNameDialogFragment() {
        EditTextDialogFragment.newInstance(getString(R.string.pref_account_edit_name_title),
            preferences_account_name_title.text.toString(), this)
            .show(requireActivity().supportFragmentManager, String.empty())
    }

    companion object {
        private const val Accounts = "|ACCOUNTS|"

        fun newInstance() = SettingsAccountFragment()
    }
}


