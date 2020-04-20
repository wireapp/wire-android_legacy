package com.waz.zclient.feature.settings.account.editphonenumber

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.empty
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.feature.settings.di.SETTINGS_SCOPE_ID

class DeletePhoneDialogFragment : DialogFragment() {

    private val phoneViewModel by sharedViewModel<SettingsAccountPhoneNumberViewModel>(SETTINGS_SCOPE_ID)

    private val phoneNumber: String by lazy {
        arguments?.getString(CURRENT_PHONE_NUMBER_KEY, String.empty()) ?: String.empty()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireActivity())
            .setTitle(getString(R.string.pref__account_action__dialog__delete_phone_or_email__confirm__title))
            .setMessage(
                getString(R.string.pref__account_action__dialog__delete_phone_or_email__confirm__message,
                    phoneNumber)
            )
            .setPositiveButton(android.R.string.ok) { _, _ ->
                phoneViewModel.onDeleteNumberButtonConfirmed()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    companion object {
        private const val CURRENT_PHONE_NUMBER_KEY = "currentPhoneNumber"

        fun newInstance(phoneNumber: String) =
            DeletePhoneDialogFragment().withArgs {
                putString(CURRENT_PHONE_NUMBER_KEY, phoneNumber)
            }
    }
}
