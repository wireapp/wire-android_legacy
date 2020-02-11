package com.waz.zclient.settings.account.deleteaccount

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.withArgs

class DeleteAccountDialogFragment : DialogFragment() {

    private val emailAddress: String by lazy {
        arguments?.getString(EMAIL_BUNDLE_KEY, String.empty()) ?: String.empty()
    }

    private val phoneNumber: String by lazy {
        arguments?.getString(PHONE_NUMBER_BUNDLE_KEY, String.empty()) ?: String.empty()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.pref_account_delete_warning_title))
            .create()
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
