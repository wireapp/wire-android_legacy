package com.waz.zclient.feature.settings.account.deleteaccount

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.core.extension.toSpanned
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.feature.settings.di.SETTINGS_SCOPE_ID

class DeleteAccountDialogFragment : DialogFragment() {

    private val deleteAccountViewModel by sharedViewModel<SettingsAccountDeleteAccountViewModel>(SETTINGS_SCOPE_ID)

    private val emailAddress: String by lazy {
        arguments?.getString(EMAIL_BUNDLE_KEY, String.empty()) ?: String.empty()
    }

    private val phoneNumber: String by lazy {
        arguments?.getString(PHONE_NUMBER_BUNDLE_KEY, String.empty()) ?: String.empty()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val message = when {
            emailAddress.isNotEmpty() -> {
                getString(R.string.delete_account_permanently_email_confirmation, emailAddress)
            }
            emailAddress.isEmpty() && phoneNumber.isNotEmpty() -> {
                getString(R.string.delete_account_permanently_sms_confirmation, phoneNumber)
            }
            else -> String.empty()
        }.toSpanned()

        return AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.pref_account_delete_warning_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.pref_account_delete_warning_verify)) { _, _ ->
                deleteAccountViewModel.onDeleteAccountConfirmed()
            }
            .setNegativeButton(getString(R.string.pref_account_delete_warning_cancel), null)
            .create()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        deleteAccountViewModel.deletionConfirmedLiveData.observe(viewLifecycleOwner) {
            val message = getString(R.string.pref_account_delete_confirmed)
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {

        private const val EMAIL_BUNDLE_KEY = "emailBundleKey"
        private const val PHONE_NUMBER_BUNDLE_KEY = "phoneNumberBundleKey"

        fun newInstance(email: String, phoneNumber: String) =
            DeleteAccountDialogFragment().withArgs {
                putString(EMAIL_BUNDLE_KEY, email)
                putString(PHONE_NUMBER_BUNDLE_KEY, phoneNumber)
            }
    }
}
