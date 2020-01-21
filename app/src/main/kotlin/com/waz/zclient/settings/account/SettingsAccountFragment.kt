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
import com.waz.zclient.settings.account.edithandle.EditHandleFragment
import kotlinx.android.synthetic.main.fragment_settings_account.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import org.koin.android.viewmodel.ext.android.viewModel

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
@Suppress("TooManyFunctions")
class SettingsAccountFragment : Fragment() {

    private val settingsAccountViewModel: SettingsAccountViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_account, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initErrorHandling()
        initAccountName()
        initAccountHandle()
        initAccountEmail()
        initAccountPhoneNumber()
        initResetPassword()
        loadProfile()
    }

    //TODO Will need changing to a phone dialog
    private fun initAccountPhoneNumber() {
        settingsAccountViewModel.phone.observe(viewLifecycleOwner) { updateAccountPhoneNumber(it) }
        preferences_account_phone.setOnClickListener {
            val title = getString(R.string.pref_account_add_phone_title)
            val defaultValue = preferences_account_phone_title.text.toString()
            showGenericEditDialog(title, defaultValue) { settingsAccountViewModel.updatePhone(it) }
        }
    }

    private fun initAccountEmail() {
        settingsAccountViewModel.email.observe(viewLifecycleOwner) { updateAccountEmail(it) }
        preferences_account_email.setOnClickListener {
            val title = getString(R.string.pref_account_add_email_title)
            val defaultValue = preferences_account_email_title.text.toString()
            showGenericEditDialog(title, defaultValue) { settingsAccountViewModel.updateEmail(it) }
        }
    }

    private fun initAccountHandle() {
        settingsAccountViewModel.handle.observe(viewLifecycleOwner) { updateAccountHandle(it) }
        preferences_account_handle.setOnClickListener {
            showEditHandleDialog()
        }
    }

    private fun initAccountName() {
        settingsAccountViewModel.name.observe(viewLifecycleOwner) { updateAccountName(it) }
        preferences_account_name.setOnClickListener {
            val title = getString(R.string.pref_account_edit_name_title)
            val defaultValue = preferences_account_name_title.text.toString()
            showGenericEditDialog(title, defaultValue) { settingsAccountViewModel.updateName(it) }
        }
    }

    private fun initResetPassword() {
        preferences_account_reset_password.setOnClickListener {
            openUrl(getString(R.string.url_password_forgot).replaceFirst(Accounts, Config.accountsUrl()))
        }
    }

    private fun initToolbar() {
        activity?.title = getString(R.string.pref_account_screen_title)
    }

    private fun initErrorHandling() {
        with(settingsAccountViewModel) {
            error.observe(viewLifecycleOwner) { errorMessage ->
                showErrorMessage(errorMessage)
            }
        }
    }

    private fun updateAccountHandle(handle: String) {
        preferences_account_handle_title.text = handle
    }

    private fun updateAccountName(name: String) {
        preferences_account_name_title.text = name
    }

    private fun updateAccountPhoneNumber(phoneState: ProfileDetail) {
        preferences_account_phone_title.text = when (phoneState) {
            ProfileDetail.EMPTY -> getString(R.string.pref_account_add_phone_title)
            else -> phoneState.value
        }
    }

    private fun updateAccountEmail(emailState: ProfileDetail) {
        preferences_account_email_title.text = when (emailState) {
            ProfileDetail.EMPTY -> getString(R.string.pref_account_add_email_title)
            else -> emailState.value
        }
    }

    private fun loadProfile() {
        lifecycleScope.launchWhenResumed {
            settingsAccountViewModel.loadProfileDetails()
        }
    }

    private fun showErrorMessage(errorMessage: String) {
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun showEditHandleDialog() {
        settingsAccountViewModel.handle.value?.let {
            EditHandleFragment.newInstance(it)
                .show(requireActivity().supportFragmentManager, String.empty())
        }
    }

    private fun showGenericEditDialog(title: String, defaultValue: String, updateFunc: (String) -> Unit) {
        EditTextDialogFragment.newInstance(title, defaultValue,
            object : EditTextDialogFragment.EditTextDialogFragmentListener {
                override fun onTextEdited(newValue: String) {
                    updateFunc(newValue)
                }
            }
        ).show(requireActivity().supportFragmentManager, String.empty())
    }

    companion object {
        private const val Accounts = "|ACCOUNTS|"

        fun newInstance() = SettingsAccountFragment()
    }
}
