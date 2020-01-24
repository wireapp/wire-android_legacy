package com.waz.zclient.settings.account.logout

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.observe
import com.waz.zclient.R
import org.koin.android.viewmodel.ext.android.viewModel

class LogoutDialogFragment : DialogFragment() {

    private val logoutDialogViewModel: LogoutDialogViewModel by viewModel()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.pref_account_sign_out_warning_message))
            .setPositiveButton(getString(R.string.pref_account_sign_out_warning_verify)) { _, _ ->
                logoutDialogViewModel.onVerifyButtonClicked()
            }
            .setNegativeButton(getString(R.string.pref_account_sign_out_warning_cancel)) { _, _ ->
                dismiss()
            }.create()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logoutDialogViewModel.logoutLiveData.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), "Logged out", LENGTH_LONG).show()
        }
    }

    companion object {
        fun newInstance() = LogoutDialogFragment()
    }
}
