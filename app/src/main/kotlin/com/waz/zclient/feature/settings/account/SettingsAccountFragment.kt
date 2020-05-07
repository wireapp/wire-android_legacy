package com.waz.zclient.feature.settings.account

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.invisible
import com.waz.zclient.core.extension.openUrl
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.core.extension.viewModel
import com.waz.zclient.core.extension.visible
import com.waz.zclient.core.ui.dialog.EditTextDialogFragment
import com.waz.zclient.feature.settings.account.deleteaccount.DeleteAccountDialogFragment
import com.waz.zclient.feature.settings.account.edithandle.EditHandleDialogFragment
import com.waz.zclient.feature.settings.account.editphonenumber.EditPhoneNumberActivity
import com.waz.zclient.feature.settings.account.logout.LogoutDialogFragment
import com.waz.zclient.feature.settings.account.logout.LogoutViewModel
import com.waz.zclient.feature.settings.di.SETTINGS_SCOPE_ID
import kotlinx.android.synthetic.main.fragment_settings_account.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
@Suppress("TooManyFunctions")
class SettingsAccountFragment : Fragment(R.layout.fragment_settings_account) {

    private val settingsAccountViewModel by viewModel<SettingsAccountViewModel>(SETTINGS_SCOPE_ID)

    private val logoutViewModel by sharedViewModel<LogoutViewModel>(SETTINGS_SCOPE_ID)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initToolbar()
        initSsoButtons()
        initTeamButtons()
        initErrorHandling()
        initAccountName()
        initAccountHandle()
        initAccountEmail()
        initAccountPhoneNumber()
        initResetPassword()
        initLogout()
        initDeleteAccountButton()
        loadProfile()
    }

    //TODO Will need changing to a phone dialog
    private fun initSsoButtons() {
        settingsAccountViewModel.isSsoAccountLiveData.observe(viewLifecycleOwner) {
            defineButtonVisibility(!it, settingsAccountEmailContainerLinearLayout)
            defineButtonVisibility(!it, settingsAccountResetPasswordButton)
            defineButtonVisibility(!it, settingsAccountDeleteAccountButton)
        }
    }

    private fun initTeamButtons() {
        settingsAccountViewModel.inATeamLiveData.observe(viewLifecycleOwner) {
            defineButtonVisibility(!it, settingsAccountPhoneContainerLinearLayout)
            defineButtonVisibility(!it, settingsAccountDeleteAccountButton)
        }
    }

    private fun defineButtonVisibility(visible: Boolean, view: View) =
        if (visible) view.visible() else view.invisible()

    private fun initAccountPhoneNumber() {
        settingsAccountViewModel.phoneNumberLiveData.observe(viewLifecycleOwner) { updateAccountPhoneNumber(it) }
        settingsAccountViewModel.phoneDialogLiveData.observe(viewLifecycleOwner) {
            when (it) {
                PhoneDialogDetail.EMPTY -> showAddPhoneDialog()
                else -> launchEditPhoneScreen(it.number, it.hasEmail)
            }
        }
        settingsAccountPhoneContainerLinearLayout.setOnClickListener {
            settingsAccountViewModel.onPhoneContainerClicked()
        }
    }

    private fun showAddPhoneDialog() {
        //Show add phone dialog here
    }

    private fun initAccountEmail() {
        settingsAccountViewModel.emailLiveData.observe(viewLifecycleOwner) { updateAccountEmail(it) }
        settingsAccountEmailContainerLinearLayout.setOnClickListener {
            val title = getString(R.string.pref_account_add_email_title)
            val defaultValue = settingsAccountEmailTitleTextView.text.toString()
            showGenericEditDialog(title, defaultValue) { settingsAccountViewModel.updateEmail(it) }
        }
    }

    private fun initAccountHandle() {
        settingsAccountViewModel.handleLiveData.observe(viewLifecycleOwner) { updateAccountHandle(it) }
        settingsAccountHandleContainerLinearLayout.setOnClickListener { showEditHandleDialog() }
    }

    private fun initAccountName() {
        settingsAccountViewModel.nameLiveData.observe(viewLifecycleOwner) { updateAccountName(it) }
        settingsAccountNameContainerLinearLayout.setOnClickListener {
            val title = getString(R.string.pref_account_edit_name_title)
            val defaultValue = settingsAccountNameTitleTextView.text.toString()
            showGenericEditDialog(title, defaultValue) { settingsAccountViewModel.updateName(it) }
        }
    }

    private fun initResetPassword() {
        settingsAccountResetPasswordButton.setOnClickListener {
            settingsAccountViewModel.onResetPasswordClicked()
        }
        settingsAccountViewModel.resetPasswordUrlLiveData.observe(viewLifecycleOwner) {
            openUrl(it)
        }
    }

    private fun initLogout() {
        observeLogoutNavigation()
        observeLogoutData()
        initLogoutButtonListener()
    }

    private fun observeLogoutNavigation() {
        settingsAccountViewModel.logoutNavigationAction.observe(viewLifecycleOwner) {
            startActivity(Intent()
                .setAction(it)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun observeLogoutData() {
        with(logoutViewModel) {
            successLiveData.observe(viewLifecycleOwner) {
                settingsAccountViewModel.onUserLoggedOut(it)
            }
            errorLiveData.observe(viewLifecycleOwner) {
                settingsAccountViewModel.onUserLogoutError(it)
            }
        }
    }

    private fun initLogoutButtonListener() =
        settingsAccountLogoutButton.setOnClickListener {
            showLogoutDialog()
        }

    private fun initDeleteAccountButton() {
        settingsAccountViewModel.deleteAccountDialogLiveData.observe(viewLifecycleOwner) {
            showDeleteAccountDialog(it.email, it.number)
        }
        settingsAccountDeleteAccountButton.setOnClickListener {
            settingsAccountViewModel.onDeleteAccountButtonClicked()
        }
    }

    private fun initToolbar() {
        activity?.title = getString(R.string.pref_account_screen_title)
    }

    private fun initErrorHandling() {
        settingsAccountViewModel.errorLiveData.observe(viewLifecycleOwner) { showErrorMessage(it) }
    }

    private fun updateAccountHandle(handle: String) {
        settingsAccountHandleTitleTextView.text = handle
    }

    private fun updateAccountName(name: String) {
        settingsAccountNameTitleTextView.text = name
    }

    private fun updateAccountPhoneNumber(phoneState: ProfileDetail) {
        settingsAccountPhoneTitleTextView.text = when (phoneState) {
            ProfileDetail.EMPTY -> getString(R.string.pref_account_add_phone_title)
            else -> phoneState.value
        }
    }

    private fun updateAccountEmail(emailState: ProfileDetail) {
        settingsAccountEmailTitleTextView.text = when (emailState) {
            ProfileDetail.EMPTY -> getString(R.string.pref_account_add_email_title)
            else -> emailState.value
        }
    }

    private fun loadProfile() {
        lifecycleScope.launchWhenResumed {
            settingsAccountViewModel.loadProfileDetails()
        }
    }

    private fun showErrorMessage(errorMessage: String) =
        Toast.makeText(requireContext(), errorMessage, LENGTH_LONG).show()

    private fun showLogoutDialog() =
        LogoutDialogFragment.newInstance()
            .show(requireActivity().supportFragmentManager, String.empty())

    private fun showEditHandleDialog() =
        EditHandleDialogFragment.newInstance(settingsAccountHandleTitleTextView.text.toString())
            .show(requireActivity().supportFragmentManager, String.empty())

    private fun showDeleteAccountDialog(email: String, phoneNumber: String) {
        DeleteAccountDialogFragment.newInstance(email, phoneNumber)
            .show(requireActivity().supportFragmentManager, String.empty())
    }

    private fun launchEditPhoneScreen(phoneNumber: String, hasEmail: Boolean) =
        startActivity(EditPhoneNumberActivity.newIntent(requireContext(), phoneNumber, hasEmail))

    private fun showGenericEditDialog(
        title: String,
        defaultValue: String,
        updateFunc: (String) -> Unit
    ) =
        EditTextDialogFragment.newInstance(title, defaultValue,
            object : EditTextDialogFragment.EditTextDialogFragmentListener {
                override fun onTextEdited(newValue: String) {
                    updateFunc(newValue)
                }
            }
        ).show(requireActivity().supportFragmentManager, String.empty())

    companion object {
        fun newInstance() = SettingsAccountFragment()
    }
}
