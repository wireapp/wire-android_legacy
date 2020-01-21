package com.waz.zclient.settings.account.phonenumber.editphone

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.waz.zclient.R
import com.waz.zclient.core.extension.withArgs
import com.waz.zclient.core.permissions.PermissionManager
import com.waz.zclient.core.permissions.extension.readPhoneState
import kotlinx.android.synthetic.main.fragment_edit_phone_dialog.*
import org.koin.android.viewmodel.ext.android.viewModel

class EditPhoneFragment : DialogFragment() {

    private val permissionManager by lazy {
        PermissionManager.newInstance(this)
    }

    private val editPhoneViewModel: EditPhoneViewModel by viewModel()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireActivity())
            .setTitle(getString(R.string.pref__account_action__dialog__edit_phone__title))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                editPhoneViewModel.onOkButtonClicked(
                    country_code_edit_text.text.toString(),
                    phone_number_edit_text.text.toString()
                )
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                editPhoneViewModel.onCancelButtonClicked()
            }.create()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_edit_phone_dialog, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    override fun onStart() {
        super.onStart()
        permissionManager.readPhoneState {
            it.fold(
                editPhoneViewModel::onReadPhonePermissionDenied,
                editPhoneViewModel::onReadPhonePermissionGranted
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {

        private const val CURRENT_PHONE_NUMBER_KEY = "currentPhoneNumber"

        fun newInstance(phoneNumber: String) =
            EditPhoneFragment().withArgs {
                putString(CURRENT_PHONE_NUMBER_KEY, phoneNumber)
            }
    }
}
