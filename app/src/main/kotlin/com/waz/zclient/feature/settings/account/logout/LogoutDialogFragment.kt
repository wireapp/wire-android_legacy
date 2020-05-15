package com.waz.zclient.feature.settings.account.logout

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.sharedViewModel
import com.waz.zclient.feature.settings.di.SETTINGS_SCOPE_ID

class LogoutDialogFragment : DialogFragment() {

    private val logoutViewModel: LogoutViewModel by sharedViewModel(SETTINGS_SCOPE_ID)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.pref_account_sign_out_warning_message))
            .setPositiveButton(getString(R.string.pref_account_sign_out_warning_verify)) { _, _ ->
                logoutViewModel.onVerifyButtonClicked()
            }
            .setNegativeButton(getString(R.string.pref_account_sign_out_warning_cancel), null)
            .create()

    companion object {
        fun newInstance() = LogoutDialogFragment()
    }
}
